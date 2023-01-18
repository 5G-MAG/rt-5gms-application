/************************************************************************************************************

* Copyright --- 2022, Dolby Laboratories Inc.

* Licensed under the License terms and conditions for use, reproduction, and distribution of 5G-MAG
* software (the “License”).
* You may not use this file except in compliance with the License. You may obtain a copy of the License
* at https://www.5g-mag.com/reference-tools.
* Unless required by applicable law or agreed to in writing, software distributed under the License is
* distributed on an “AS IS” BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and limitations under the License.

 ************************************************************************************************************/


package com.google.android.exoplayer2.demo;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.exoplayer2.util.Log;


public class WebviewActivity extends AppCompatActivity {

  String landing_url = "http://stage.sofiadigital.fi/dvb/dvb-i-reference-application/frontend/android/player.html";

  @RequiresApi(api = Build.VERSION_CODES.KITKAT)
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    //0 Call application context, to be passed to NetworkOps constructor
    // NetworkOps will take care to download the service list into the app cache memory
    Context context = this.getApplicationContext();
    new NetworkOps(context).execute();

    //1. Set myWebView
    WebView myWebView = new WebView(this);
    setContentView(myWebView);
    myWebView.setWebViewClient(new MyWebViewClient());

    //2a. Enable JavaScript support and allow Dynamic contents being displayed
    WebSettings webSettings = myWebView.getSettings();
    webSettings.setJavaScriptEnabled(true);
    webSettings.setDomStorageEnabled(true);

    //2b. Handle JavaScript component
    myWebView.addJavascriptInterface(new JavaScriptInterface(this), "JavaScriptInterface");

    //3. Load the landing DVB-I page
    myWebView.loadUrl(landing_url);

  }

  @Override
  protected void onResume() {

    //Restart the whole Webview activity, so it is possible to playback the same test stream again
    //There is probably a better way to do this
    WebView myWebView = new WebView(this);
    setContentView(myWebView);
    myWebView.setWebViewClient(new MyWebViewClient());
    WebSettings webSettings = myWebView.getSettings();
    webSettings.setJavaScriptEnabled(true);
    webSettings.setDomStorageEnabled(true);
    myWebView.addJavascriptInterface(new JavaScriptInterface(this), "JavaScriptInterface");
    myWebView.loadUrl(landing_url);
    super.onResume();
  }


  //MyWebViewClient class is used to introduce different intent handling behavior
  private class MyWebViewClient extends WebViewClient {

    public WebResourceResponse shouldInterceptRequest (WebView view, String url) {
      String[] extensions = {".mpd", ".m3u8", ".mp4", ".ts"};
      for (String item : extensions) {
        if (url.contains(item)) {
          Log.d("dlb", "Intercepted stream : " + url + "will be handled via Dolby modified Exoplayer (incl. DAX AC-4 DE control)");
          Uri url_temp = Uri.parse(url);
          Intent intent = new Intent(WebviewActivity.this, PlayerActivity.class);
          intent.setData(url_temp);
          intent.setAction("com.google.android.exoplayer.demo.action.VIEW");
          try {
            startActivity(intent);
          } catch (ActivityNotFoundException e){
            // Define what your app should do if no activity can handle the intent.
          }
          return new WebResourceResponse(null, null, null);
        } else {
          return null;
        }
      }
      return null;
    }
  }


  public class JavaScriptInterface {
    Context mContext;
    /** Instantiate the interface and set the context */
    JavaScriptInterface(Context c) {
      mContext = c;
    }
  }

}

