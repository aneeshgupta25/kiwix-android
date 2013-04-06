package org.kiwix.kiwixmobile;


import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Currency;

import android.app.ActionBar.LayoutParams;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.LabeledIntent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebBackForwardList;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;


public class KiwixMobileActivity extends Activity {
    /** Called when the activity is first created. */

	private WebView webView;
	private ArrayAdapter<String> adapter;
	protected boolean requestClearHistoryAfterLoad;
	private static final int ZIMFILESELECT_REQUEST_CODE = 1234;
	private static final int PREFERENCES_REQUEST_CODE = 1235;
	private static final String PREFS_KIWIX_MOBILE = "kiwix-mobile";
	private AutoCompleteTextView articleSearchtextView;
	private LinearLayout articleSearchBar;


	public class AutoCompleteAdapter extends ArrayAdapter<String> implements Filterable {
		private ArrayList<String> mData;

		public AutoCompleteAdapter(Context context, int textViewResourceId) {
			super(context, textViewResourceId);
			mData = new ArrayList<String>();
		}

		@Override
		public int getCount() {
			return mData.size();
		}

		@Override
		public String getItem(int index) {
			return mData.get(index);
		}

		@Override
		public Filter getFilter() {
			Filter myFilter = new Filter() {
				@Override
				protected FilterResults performFiltering(CharSequence constraint) {
					FilterResults filterResults = new FilterResults();
					ArrayList<String> data = new ArrayList<String>();
					if(constraint != null) {
						// A class that queries a web API, parses the data and returns an ArrayList<Style>
						try {
							ZimContentProvider.searchSuggestions(constraint.toString(), 20);
							String suggestion;

							data.clear();
							while ((suggestion = ZimContentProvider.getNextSuggestion())!=null) {
								data.add(suggestion);
							}
						}
						catch(Exception e) {}
						// Now assign the values and count to the FilterResults object
						filterResults.values = data;
						filterResults.count = data.size();
					}
					return filterResults;
				}

				@SuppressWarnings("unchecked")
				@Override
				protected void publishResults(CharSequence contraint, FilterResults results) {
					if(results != null && results.count > 0) {
						notifyDataSetChanged();
						mData = (ArrayList<String>)results.values;
					}
					else {
						notifyDataSetInvalidated();
					}
				}
			};
			return myFilter;
		}
	}
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestClearHistoryAfterLoad=false;


        this.requestWindowFeature(Window.FEATURE_PROGRESS);
        this.setProgressBarVisibility(true);

        setContentView(R.layout.main);
        webView = (WebView) findViewById(R.id.webview);
        articleSearchBar = (LinearLayout) findViewById(R.id.articleSearchBar);
        articleSearchtextView = (AutoCompleteTextView) findViewById(R.id.articleSearchTextView);


        // Create the adapter and set it to the AutoCompleteTextView
        adapter = new AutoCompleteAdapter(this, android.R.layout.simple_list_item_1);

        articleSearchtextView.setAdapter(adapter);
        articleSearchtextView.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				articleSearchtextView.setText(parent.getItemAtPosition(position).toString());
				openArticle();
			}
		});
        articleSearchtextView.setOnEditorActionListener(new OnEditorActionListener() {

            @Override
            public boolean onEditorAction(TextView v, int actionId,
                    KeyEvent event) {
            		//Do Stuff
            		return openArticle();
         }});


        // js includes will not happen unless we enable JS
        webView.getSettings().setJavaScriptEnabled(true);
        //Does not seem to have  impact. (Idea was that
        // web page is rendered before loading all pictures)
        //webView.getSettings().setRenderPriority(RenderPriority.HIGH);
        final Activity activity = this;

        webView.setWebChromeClient(new WebChromeClient(){

				public void onProgressChanged(WebView view, int progress) {
                	 	 activity.setProgress(progress * 100);
                         if (progress==100) {

                        	 Log.d("kiwix", "Loading article finished.");
                        	 if (requestClearHistoryAfterLoad) {
                        		 Log.d("kiwix", "Loading article finished and requestClearHistoryAfterLoad -> clearHistory");
                        		 webView.clearHistory();
                        		 requestClearHistoryAfterLoad=false;
                        	 }
                         }
                         }
        });

//       Should basically resemble the behavior when setWebClient not done
//            (i.p. internal urls load in webview, external urls in browser)
// 			  as currently no custom setWebViewClient required it is commented
        	webView.setWebViewClient(new WebViewClient() {

        	@Override
        	public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.startsWith(ZimContentProvider.CONTENT_URI.toString())) {
                    // This is my web site, so do not override; let my WebView load the page
                    return false;
                } else if (url.startsWith("file://")) {
                    // To handle help page (loaded from resources)
                    return true;
                } else if (url.startsWith(ZimContentProvider.UI_URI.toString())) {
                	// To handle links which access user interface (i.p. used in help page)
                	if (url.equals(ZimContentProvider.UI_URI.toString()+"selectzimfile")) {
                		selectZimFile();
                	} else if (url.equals(ZimContentProvider.UI_URI.toString()+"gotohelp")) {
                		showHelp();
                	} else {
                		Log.e("kiwix", "UI Url "+url+ " not supported.");
                	}
                	return true;
                }
                // Otherwise, the link is not for a page on my site, so launch another Activity that handles URLs
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(intent);
                return true;
            }

        	public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
        	     String errorString = String.format(getResources().getString(R.string.error_articleurlnotfound), failingUrl);
        	     //TODO apparently screws up back/forward
        	     webView.loadDataWithBaseURL("file://error","<html><body>"+errorString+"</body></html>", "text/html", "utf-8", failingUrl);
        	     String title = getResources().getString(R.string.app_name);
        	     getActionBar().setTitle(title);
        	   }

        	public void onPageFinished(WebView view, String url) {
        		String title = getResources().getString(R.string.app_name);
        		if (webView.getTitle()!=null && !webView.getTitle().isEmpty())
        			title = webView.getTitle();
        		getActionBar().setTitle(title);
        	}
        	 });

        loadPref();
      //Pinch to zoom
        webView.getSettings().setBuiltInZoomControls(true);
        //webView.getSettings().setLoadsImagesAutomatically(false);
        //Does not make much sense to cache data from zim files.(Not clear whether
        // this actually has any effect)
        webView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
        if (getIntent().getData()!=null) {        	
        	String filePath = getIntent().getData().getEncodedPath();
            Log.d("kiwix", " Kiwix started from a filemanager. Intent filePath: "+filePath+" -> open this zimfile and load main page");
            openZimFile(new File(filePath), false);

        } else if (savedInstanceState!=null) {
        	 Log.d("kiwix", " Kiwix started with a savedInstanceState (That is was closed by OS) -> restore webview state and zimfile (if set)");
        	 if (savedInstanceState.getString("currentzimfile")!=null) {
	        	 	openZimFile(new File(savedInstanceState.getString("currentzimfile")), false);

	         }
        	 // Restore the state of the WebView

	         webView.restoreState(savedInstanceState);
        } else {
        	SharedPreferences settings = getSharedPreferences(PREFS_KIWIX_MOBILE, 0);
        	String zimfile = settings.getString("currentzimfile", null);
            if (zimfile != null) {
            	Log.d("kiwix", " Kiwix normal start, zimfile loaded last time -> Open last used zimfile "+zimfile);
            	openZimFile(new File(zimfile), false);
            	// Alternative would be to restore webView state. But more effort to implement, and actually
        		//  fits better normal android behavior if after closing app ("back" button) state is not maintained.
            } else {
            	Log.d("kiwix", " Kiwix normal start, no zimfile loaded last time  -> display welcome page");
            	showWelcome();
            }
        }
        
    }


    private void loadPref(){
    	  SharedPreferences mySharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
    	  String pref_zoom = mySharedPreferences.getString("pref_zoom", "automatic");
    	  if (pref_zoom.equals("automatic")) {
      		 setDefaultZoom();
    	  } else if (pref_zoom.equals("medium")) {
    		  webView.getSettings().setDefaultZoom(WebSettings.ZoomDensity.MEDIUM);
    	  } else if (pref_zoom.equals("small")) {
    		  webView.getSettings().setDefaultZoom(WebSettings.ZoomDensity.FAR);    	  
    	  } else if (pref_zoom.equals("large")) {
    		  webView.getSettings().setDefaultZoom(WebSettings.ZoomDensity.CLOSE);
    	 } else {
    		 Log.w("kiwix", "pref_displayZoom value ("+pref_zoom+" unknown. Assuming automatic");
    		 webView.getSettings().setDefaultZoom(WebSettings.ZoomDensity.MEDIUM);
    	 }
    }
    

    @Override
    public void onPause() {
    	super.onPause();
    	SharedPreferences settings = getSharedPreferences(PREFS_KIWIX_MOBILE, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString("currentzimfile", ZimContentProvider.getZimFile());
        // Commit the edits!
        editor.commit();

    	Log.d("kiwix", "onPause Save currentzimfile to preferences:"+ZimContentProvider.getZimFile());
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
       super.onSaveInstanceState(outState);
    	// Save the state of the WebView

        webView.saveState(outState);
        outState.putString("currentzimfile", ZimContentProvider.getZimFile());
        Log.v("kiwix", "onSaveInstanceState Save currentzimfile to bundle:"+ZimContentProvider.getZimFile()+" and webView state");
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
    	super.onRestoreInstanceState(savedInstanceState);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                Toast.makeText(this, "Tapped home", Toast.LENGTH_SHORT).show();
                break;
            case R.id.menu_search:
            	if (articleSearchBar.getVisibility()!=View.VISIBLE) {
            		showSearchBar();
            	} else {
            		hideSearchBar();
            	}
            	break;
            case R.id.menu_searchintext:
            	webView.showFindDialog("", true);
            	break;
            case R.id.menu_home:
            	loadMainPage();
            	break;
            case R.id.menu_forward:
            	if(webView.canGoForward() == true){
                    webView.goForward();
                }
                break;
            case R.id.menu_back:
            	if(webView.canGoBack() == true){
                    webView.goBack();
                }
                break;
            case R.id.menu_help:
            	showHelp();
            	break;
            case R.id.menu_openfile:

			    selectZimFile();
			    break;

            case R.id.menu_settings:
            	// Display the fragment as the main content.
            	Intent i = new Intent(this, KiwixSettings.class);
                startActivityForResult(i, PREFERENCES_REQUEST_CODE);
            	break;

        }
        return super.onOptionsItemSelected(item);
    }




	private void selectZimFile() {
		final Intent target = new Intent(Intent.ACTION_GET_CONTENT);
		// The MIME data type filter
		target.setType("*/*");
		// Only return URIs that can be opened with ContentResolver
		target.addCategory(Intent.CATEGORY_OPENABLE);
		//Force use of our file selection component.
		// (Note may make sense to just define a custom intent instead)
		target.setComponent(new ComponentName(getPackageName(), getPackageName()+".ZimFileSelectActivity"));
		try {
			startActivityForResult(target, ZIMFILESELECT_REQUEST_CODE);
		} catch (ActivityNotFoundException e) {

		}
	}




	private void showSearchBar() {
		articleSearchBar.setVisibility(View.VISIBLE);
		articleSearchtextView.requestFocus();
		//Move cursor to end
		articleSearchtextView.setSelection(articleSearchtextView.getText().length());
		InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
		imm.toggleSoftInput(InputMethodManager.SHOW_FORCED,0);
	}

    private String readTextFromResource(int resourceID)
    	{
    	    InputStream raw = getResources().openRawResource(resourceID);
    	    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    	    int i;
    	    try
    	    {
    	        i = raw.read();
    	        while (i != -1)
    	        {
    	            stream.write(i);
    	            i = raw.read();
    	        }
    	        raw.close();
    	    }
    	    catch (IOException e)
    	    {
    	        e.printStackTrace();
    	    }
    	    return stream.toString();
    }

    private void showWelcome() {
    	webView.loadUrl("file:///android_res/raw/welcome.html");
	}

    private void showHelp() {
    	//Load from resource. Use with base url as else no images can be embedded.
    	// Note that this leads inclusion of welcome page in browser history
    	//   This is not perfect, but good enough. (and would be signifcant
    	// effort to remove file)
    	webView.loadUrl("file:///android_res/raw/help.html");
	}


	@Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
        case ZIMFILESELECT_REQUEST_CODE:
            if (resultCode == RESULT_OK) {
                // The URI of the selected file
                final Uri uri = data.getData();
                File file = null;
                if (uri != null) {
                	String path = uri.getPath();
                	if (path != null)
                		file = new File(path);
                }
                if (file==null)
                	return;
                // Create a File from this Uri
                openZimFile(file, true);
            }
            break;
        case PREFERENCES_REQUEST_CODE:
            
             loadPref();
        	break;
        }
    }




	private boolean openZimFile(File file, boolean clearHistory) {
		if (file.exists()) {
			if (ZimContentProvider.setZimFile(file.getAbsolutePath())!=null) {


        		getActionBar().setSubtitle(ZimContentProvider.getZimFileTitle());
				//Apparently with webView.clearHistory() only
				//    history before currently (fully) loaded page is cleared
				// -> request clear, actual clear done after load.
				//    Probably not working in all corners (e.g. zim file openend
				//    while load in progress, mainpage of new zim file invalid, ...
				//       but should be good enough.
				// Actually probably redundant if no zim file openend before in session,
				//  but to be on save side don't clear history in such cases.
				if (clearHistory)
					requestClearHistoryAfterLoad=true;
				loadMainPage();
				return true;
			} else {
				Toast.makeText(this, getResources().getString(R.string.error_fileinvalid), Toast.LENGTH_LONG).show();
			}

		} else {
			Toast.makeText(this, getResources().getString(R.string.error_filenotfound), Toast.LENGTH_LONG).show();
		}
		return false;
	}

    private void loadMainPage() {
    	String article = ZimContentProvider.getMainPage();
        webView.loadUrl(Uri.parse(ZimContentProvider.CONTENT_URI
                + article).toString());
	}


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if(event.getAction() == KeyEvent.ACTION_DOWN){
            switch(keyCode)
            {
            case KeyEvent.KEYCODE_BACK:
                if(webView.canGoBack() == true){
                	/*WebBackForwardList history = webView.copyBackForwardList();

                	if (history.getCurrentIndex() )*/

                    webView.goBack();
                }else{
                    finish();
                }
                return true;
            }

        }
        return super.onKeyDown(keyCode, event);
    }




	private boolean openArticle() {
		Log.d("kiwix", articleSearchtextView+" onEditorAction. "+articleSearchtextView.getText());

		String articleUrl = ZimContentProvider.getPageUrlFromTitle(articleSearchtextView.getText().toString());
		Log.d("kiwix", articleSearchtextView+" onEditorAction. TextView: "+articleSearchtextView.getText()+ " articleUrl: "+articleUrl);

		if (articleUrl!=null) {
			hideSearchBar();
			webView.loadUrl(Uri.parse(ZimContentProvider.CONTENT_URI
		            +articleUrl).toString());
			return true;
		} else {
			String errorString = String.format(getResources().getString(R.string.error_articlenotfound), articleSearchtextView.getText().toString());
			Toast.makeText(getWindow().getContext(), errorString, Toast.LENGTH_SHORT).show();

			return true;
		}
	}



	public boolean isTablet(Context context) {
	    return (context.getResources().getConfiguration().screenLayout
	            & Configuration.SCREENLAYOUT_SIZE_MASK)
	            >= Configuration.SCREENLAYOUT_SIZE_LARGE;
	}

	private void hideSearchBar() {
		// Hide searchbar
		articleSearchBar.setVisibility(View.GONE);
		// To close softkeyboard
		webView.requestFocus();
		//Seems not really be necessary
		InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
		imm.hideSoftInputFromWindow(articleSearchtextView.getWindowToken(),0);
	}
	
	private void setDefaultZoom() {
		DisplayMetrics metrics = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(metrics);
	
		//Cleaner than approach used in 1.0 to set CLOSE for tables, MEDIUM for phones.
		// However, unfortunately at least on Samsung Galaxy Tab 2 density is medium.
		// Anyway, user can now override so it should be ok.
		switch (metrics.densityDpi) {
		case DisplayMetrics.DENSITY_HIGH:
			Log.d("kiwix", "setDefaultZoom for Display DENSITY_HIGH-> ZoomDensity.FAR ");
		    webView.getSettings().setDefaultZoom(WebSettings.ZoomDensity.FAR);
		    break;
	
		case DisplayMetrics.DENSITY_MEDIUM:
			Log.d("kiwix", "setDefaultZoom for Display DENSITY_MEDIUM-> ZoomDensity.MEDIUM ");
		    webView.getSettings().setDefaultZoom(WebSettings.ZoomDensity.MEDIUM);
		    break;
	
		case DisplayMetrics.DENSITY_LOW:
			Log.d("kiwix", "setDefaultZoom for Display DENSITY_LOW-> ZoomDensity.CLOSE ");
		    webView.getSettings().setDefaultZoom(WebSettings.ZoomDensity.CLOSE);
		    break;
	
		default:
			Log.d("kiwix", "setDefaultZoom for Display OTHER -> ZoomDensity.MEDIUM ");
		    webView.getSettings().setDefaultZoom(WebSettings.ZoomDensity.MEDIUM);
		    break;
		}
	}
}
	
