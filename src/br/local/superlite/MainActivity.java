package br.local.superlite;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.net.Uri;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.webkit.*;
import android.widget.*;
import android.text.InputType;
import java.net.URLEncoder;

public final class MainActivity extends Activity {
    private WebView web;
    private EditText address;
    private ProgressBar progress;
    private TextView status;
    private Button back, forward;
    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density); }
    private Button button(String text, LinearLayout row, Runnable action) {
        Button b = new Button(this); b.setText(text); b.setTextSize(16);
        b.setMinWidth(0); b.setMinimumWidth(0); b.setPadding(0,0,0,0);
        row.addView(b, new LinearLayout.LayoutParams(dp(48), dp(48)));
        b.setOnClickListener(v -> action.run()); return b;
    }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(Color.rgb(240,244,248));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(240,244,248));
        LinearLayout top = new LinearLayout(this); top.setPadding(dp(6),0,dp(6),0);
        address = new EditText(this); address.setSingleLine(true); address.setTextSize(15);
        address.setHint("Pesquisar ou digitar endereço"); address.setSelectAllOnFocus(true);
        address.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        address.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_GO);
        top.addView(address, new LinearLayout.LayoutParams(0,dp(52),1));
        button("Ir",top,()->navigate(address.getText().toString())); root.addView(top,new LinearLayout.LayoutParams(-1,dp(52)));
        progress = new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        root.addView(progress,new LinearLayout.LayoutParams(-1,dp(3)));
        web = new WebView(this); root.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        status = new TextView(this); status.setTextSize(12); status.setPadding(dp(12),dp(2),dp(12),dp(2));
        status.setVisibility(View.GONE); root.addView(status);
        LinearLayout bottom = new LinearLayout(this);
        back=button("‹",bottom,()->{if(web.canGoBack())web.goBack();});
        forward=button("›",bottom,()->{if(web.canGoForward())web.goForward();});
        button("↻",bottom,()->web.reload()); button("⌂",bottom,()->home());
        TextView name = new TextView(this); name.setText("SuperLite"); name.setTextSize(14); name.setGravity( android.view.Gravity.CENTER);
        bottom.addView(name,new LinearLayout.LayoutParams(0,dp(48),1)); root.addView(bottom,new LinearLayout.LayoutParams(-1,dp(48))); setContentView(root);
        WebSettings s=web.getSettings(); s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false); s.setAllowContentAccess(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setSupportZoom(true); s.setBuiltInZoomControls(true); s.setDisplayZoomControls(false);
        s.setUseWideViewPort(true); s.setLoadWithOverviewMode(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r){
                String scheme=r.getUrl().getScheme();
                if("https".equals(scheme)||"http".equals(scheme))return false;
                Toast.makeText(MainActivity.this,"Link externo não suportado",Toast.LENGTH_SHORT).show(); return true;
            }
            @Override public void onPageStarted(WebView v,String url,android.graphics.Bitmap icon){status.setVisibility(View.GONE); update(url);}
            @Override public void onPageFinished(WebView v,String url){update(url);}
            @Override public void onReceivedError(WebView v,WebResourceRequest r,WebResourceError e){
                if(r.isForMainFrame()){status.setText("Não foi possível abrir. Verifique a conexão ou tente outro endereço.");status.setVisibility(View.VISIBLE);}
            }
        });
        web.setWebChromeClient(new WebChromeClient(){@Override public void onProgressChanged(WebView v,int n){progress.setProgress(n);progress.setVisibility(n==100?View.GONE:View.VISIBLE);}});
        address.setOnEditorActionListener((v,action,event)->{if(action==android.view.inputmethod.EditorInfo.IME_ACTION_GO){navigate(address.getText().toString());return true;}return false;});
        if(saved==null || web.restoreState(saved)==null)home();
    }
    private void update(String url){if(!address.hasFocus())address.setText(url!=null&&url.startsWith("https://superlite.local")?"":url);back.setEnabled(web.canGoBack());forward.setEnabled(web.canGoForward());}
    private void navigate(String value){
        String input=value.trim(); if(input.isEmpty())return;
        String url;
        if(input.matches("(?i)^https?://.*"))url=input;
        else if(!input.contains(" ") && input.contains(".") && !input.contains(":"))url="https://"+input;
        else {try{url="https://www.google.com/search?q="+URLEncoder.encode(input,"UTF-8");}catch(Exception e){return;}}
        address.clearFocus(); ((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(address.getWindowToken(),0); web.requestFocus(); web.loadUrl(url);
    }
    private void home(){address.clearFocus();web.loadDataWithBaseURL("https://superlite.local/","<!doctype html><html lang='pt-BR'><meta name='viewport' content='width=device-width, initial-scale=1'><body style='margin:0;background:#f0f4f8;color:#172b40;font-family:sans-serif'><main style='padding:64px 24px'><div style='font-size:12px;letter-spacing:3px;color:#42786b'>LEVE POR NATUREZA</div><h1 style='font-size:44px;margin:16px 0'>SuperLite<span style='color:#149b77'>.</span></h1><p style='line-height:1.6;color:#516577'>Sua internet, sem excesso.<br>Pesquise ou digite um endereço na barra acima.</p><p style='margin-top:44px'><a style='color:#087d61' href='https://www.google.com'>Google</a> &nbsp; · &nbsp; <a style='color:#087d61' href='https://pt.wikipedia.org'>Wikipédia</a></p></main></body></html>","text/html","UTF-8",null);}
    @Override public void onBackPressed(){if(web.canGoBack())web.goBack();else super.onBackPressed();}
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);web.saveState(out);}
    @Override protected void onPause(){web.onPause();super.onPause();}
    @Override protected void onResume(){super.onResume();if(web!=null)web.onResume();}
    @Override protected void onDestroy(){web.destroy();super.onDestroy();}
}
