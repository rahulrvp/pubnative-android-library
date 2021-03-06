/**
 * Copyright 2014 PubNative GmbH
 * <p/>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p/>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p/>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package net.pubnative.library.request;

import android.content.Context;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.util.DisplayMetrics;

import net.pubnative.library.PubnativeContract;
import net.pubnative.library.model.NativeAdModel;
import net.pubnative.library.task.AsyncHttpTask;
import net.pubnative.library.util.Crypto;
import net.pubnative.library.util.IdUtil;

import org.droidparts.persist.serializer.JSONSerializer;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

public class AdRequest implements Serializable, AsyncHttpTask.AsyncHttpTaskListener, IdUtil.AndroidAdvertisingIDTask.AndroidAdvertisingIDTaskListener
{
    String BASE_URL            = "http://api.pubnative.net/api/partner/v2/promotions";
    String NATIVE_ENDPOINT_URL = "native";
    String VIDEO_ENDPOINT_URL  = "video";

    public enum Endpoint
    {
        NATIVE, VIDEO
    }

    private static final long                    serialVersionUID  = 1L;
    private final        HashMap<String, String> requestParameters = new HashMap<String, String>();
    private              AdRequestListener       listener          = null;
    private              Endpoint                endpoint          = null;
    private              Context                 context           = null;

    // Public methods
    public AdRequest(Context context)
    {
        this.context = context;
    }

    public void setParameter(String key, String val)
    {
        if (val == null)
        {
            requestParameters.remove(key);
        }
        else
        {
            requestParameters.put(key, val);
        }
    }

    public Endpoint getEndpoint()
    {
        return this.endpoint;
    }

    public void start(final Endpoint endpoint, AdRequestListener listener)
    {
        this.listener = listener;
        this.endpoint = endpoint;
        if (this.listener == null)
        {
            this.invokeOnAdRequestFailed(new Exception("Listener not specified, interrupting request"));
        }
        else
        {
            this.invokeOnAdRequestStarted();
            // UI THREAD
            this.fillInDefaultls();
            // BACKGROUND THREAD
            // Since the android_advertiser id must be checked in background
            // thread,
            // we go to a secondary thread to do this request and then come back
            // to start the request
            IdUtil.getAndroidAdvertisingID(this.context, this);
        }
    }

    @Override
    public void onAndroidAdvertisingIDTaskFinished(String result)
    {
        if (!TextUtils.isEmpty(result))
        {
            this.requestParameters.put(PubnativeContract.Request.ANDROID_ADVERTISER_ID, result);
            this.requestParameters.put(PubnativeContract.Request.ANDROID_ADVERTISER_ID_SHA1, Crypto.sha1(result));
            this.requestParameters.put(PubnativeContract.Request.ANDROID_ADVERTISER_ID_MD5, Crypto.md5(result));
        }
        else
        {
            this.requestParameters.put(PubnativeContract.Request.NO_USER_ID, "1");
        }

        AsyncHttpTask requestTask = new AsyncHttpTask(AdRequest.this.context);
        requestTask.setListener(this);
        requestTask.execute(this.toString());
    }

    @Override
    public void onAsyncHttpTaskFinished(AsyncHttpTask task, String result)
    {
        if(!TextUtils.isEmpty(result))
        {
            try
            {
                JSONObject jsonObject = new JSONObject(result);
                JSONArray arr = jsonObject.getJSONArray(PubnativeContract.Response.ADS);
                JSONSerializer jsonSerializer = new JSONSerializer(NativeAdModel.class, this.context);
                ArrayList<NativeAdModel> ads = (ArrayList<NativeAdModel>) jsonSerializer.deserializeAll(arr);
                this.invokeOnAdRequestFinished(ads);
            }
            catch (Exception e)
            {
                this.invokeOnAdRequestFailed(e);
            }
        }
        else
        {
            this.invokeOnAdRequestFailed(new Exception("Pubnative - Error: empty response"));
        }
    }

    @Override
    public void onAsyncHttpTaskFailed(AsyncHttpTask task, Exception e)
    {
        this.invokeOnAdRequestFailed(e);
    }

    private void fillInDefaultls()
    {
        if (!this.requestParameters.containsKey(PubnativeContract.Request.BUNDLE_ID))
        {
            this.requestParameters.put(PubnativeContract.Request.BUNDLE_ID, IdUtil.getPackageName(this.context));
        }
        if (!this.requestParameters.containsKey(PubnativeContract.Request.OS))
        {
            this.requestParameters.put(PubnativeContract.Request.OS, "android");
        }
        if (!this.requestParameters.containsKey(PubnativeContract.Request.OS_VERSION))
        {
            this.requestParameters.put(PubnativeContract.Request.OS_VERSION, Build.VERSION.RELEASE);
        }
        if (!this.requestParameters.containsKey(PubnativeContract.Request.DEVICE_MODEL))
        {
            this.requestParameters.put(PubnativeContract.Request.DEVICE_MODEL, Build.MODEL);
        }
        if (!this.requestParameters.containsKey(PubnativeContract.Request.DEVICE_RESOLUTION))
        {
            DisplayMetrics dm = this.context.getResources().getDisplayMetrics();
            this.requestParameters.put(PubnativeContract.Request.DEVICE_RESOLUTION, dm.widthPixels + "x" + dm.heightPixels);
        }
        if (!this.requestParameters.containsKey(PubnativeContract.Request.DEVICE_TYPE))
        {
            this.requestParameters.put(PubnativeContract.Request.DEVICE_TYPE, IdUtil.isTablet(this.context) ? "tablet" : "phone");
        }
        if (!this.requestParameters.containsKey(PubnativeContract.Request.LOCALE))
        {
            this.requestParameters.put(PubnativeContract.Request.LOCALE, Locale.getDefault().getLanguage());
        }
        Location location = IdUtil.getLastLocation(this.context);
        if (location != null)
        {
            if (!this.requestParameters.containsKey(PubnativeContract.Request.LAT))
            {
                this.requestParameters.put(PubnativeContract.Request.LAT, String.valueOf(location.getLatitude()));
            }
            if (!this.requestParameters.containsKey(PubnativeContract.Request.LONG))
            {
                this.requestParameters.put(PubnativeContract.Request.LONG, String.valueOf(location.getLongitude()));
            }
        }
    }

    @Override
    public String toString()
    {
        Uri.Builder uriBuilder = Uri.parse(this.BASE_URL).buildUpon();
        switch (endpoint)
        {
            case NATIVE:
                uriBuilder.appendPath(this.NATIVE_ENDPOINT_URL);
                break;
            case VIDEO:
                uriBuilder.appendPath(this.NATIVE_ENDPOINT_URL);
                uriBuilder.appendPath(this.VIDEO_ENDPOINT_URL);
                break;
            default:
                throw new IllegalArgumentException(endpoint.toString());
        }
        for (String key : requestParameters.keySet())
        {
            String value = requestParameters.get(key);
            if (value != null)
            {
                uriBuilder.appendQueryParameter(key, value);
            }
        }
        return uriBuilder.build().toString();
    }

    private void invokeOnAdRequestStarted()
    {
        if (this.listener != null)
        {
            this.listener.onAdRequestStarted(this);
        }
    }

    private void invokeOnAdRequestFinished(ArrayList<? extends NativeAdModel> ads)
    {
        if (this.listener != null)
        {
            this.listener.onAdRequestFinished(this, ads);
        }
    }

    private void invokeOnAdRequestFailed(Exception e)
    {
        if (this.listener != null)
        {
            this.listener.onAdRequestFailed(this, e);
        }
    }
}
