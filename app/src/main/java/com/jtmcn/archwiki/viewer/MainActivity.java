package com.jtmcn.archwiki.viewer;

import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.core.view.MenuItemCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.webkit.WebSettings;
import android.widget.ProgressBar;

import com.jtmcn.archwiki.viewer.data.SearchResult;
import com.jtmcn.archwiki.viewer.data.SearchResultsBuilder;
import com.jtmcn.archwiki.viewer.data.WikiPage;
import com.jtmcn.archwiki.viewer.tasks.Fetch;
import com.jtmcn.archwiki.viewer.tasks.FetchUrl;
import com.jtmcn.archwiki.viewer.utils.SettingsUtils;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;

import static com.jtmcn.archwiki.viewer.Constants.TEXT_PLAIN_MIME;

public class MainActivity extends AppCompatActivity implements FetchUrl.OnFinish<List<SearchResult>> {
	public static final String TAG = MainActivity.class.getSimpleName();
	@BindView(R.id.wiki_view) WikiView wikiViewer;
	@BindView(R.id.toolbar) Toolbar toolbar;
	private SearchView searchView;
	private MenuItem searchMenuItem;
	private List<SearchResult> currentSuggestions;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		ButterKnife.bind(this);

		setSupportActionBar(toolbar);

		ProgressBar progressBar = findViewById(R.id.progress_bar);
		wikiViewer.buildView(progressBar, getSupportActionBar());

		handleIntent(getIntent());
	}

	@Override
	protected void onResume() {
		super.onResume();
		updateWebSettings();
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		handleIntent(intent);
	}

	private void handleIntent(Intent intent) {
		if (intent == null) {
			return;
		}

		if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
			String query = intent.getStringExtra(SearchManager.QUERY);
			wikiViewer.passSearch(query);
			hideSearchView();
		} else if (Intent.ACTION_VIEW.equals(intent.getAction())) {
			final String url = intent.getDataString();
			wikiViewer.wikiClient.shouldOverrideUrlLoading(wikiViewer, url);
		}
	}

	/**
	 * Update the font size used in the webview.
	 */
	public void updateWebSettings() {
		WebSettings webSettings = wikiViewer.getSettings();
		int fontSize = SettingsUtils.getFontSize(this);

		//todo this setting should be changed to a slider, remove deprecated call
		// deprecated method must be used until Android API 14
		// https://developer.android.com/reference/android/webkit/WebSettings.TextSize.html#NORMAL
		switch (fontSize) {
			case 0:
				webSettings.setTextSize(WebSettings.TextSize.SMALLEST); //50%
				break;
			case 1:
				webSettings.setTextSize(WebSettings.TextSize.SMALLER); //75%
				break;
			case 2:
				webSettings.setTextSize(WebSettings.TextSize.NORMAL); //100%
				break;
			case 3:
				webSettings.setTextSize(WebSettings.TextSize.LARGER); //150%
				break;
			case 4:
				webSettings.setTextSize(WebSettings.TextSize.LARGEST); //200%
				break;
		}
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
		searchMenuItem = menu.findItem(R.id.menu_search);
		searchView = (SearchView) MenuItemCompat.getActionView(searchMenuItem);
		searchView.setOnQueryTextFocusChangeListener((v, hasFocus) -> {
			if (!hasFocus) {
				hideSearchView();
			}
		});
		searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
		searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
			@Override
			public boolean onQueryTextSubmit(String query) {
				wikiViewer.passSearch(query);
				return false;
			}

			@Override
			public boolean onQueryTextChange(String newText) {
				if (newText.isEmpty()) {
					setCursorAdapter(new ArrayList<>());
					return true;
				} else {
					String searchUrl = SearchResultsBuilder.getSearchQuery(newText);
					Fetch.search(MainActivity.this, searchUrl);
					return true;
				}
			}
		});

		searchView.setOnSuggestionListener(new SearchView.OnSuggestionListener() {
			@Override
			public boolean onSuggestionSelect(int position) {
				return false;
			}

			@Override
			public boolean onSuggestionClick(int position) {
				SearchResult searchResult = currentSuggestions.get(position);
				Log.d(TAG, "Opening '" + searchResult.getPageName() + "' from search suggestion.");
				wikiViewer.wikiClient.shouldOverrideUrlLoading(wikiViewer, searchResult.getPageURL());
				hideSearchView();
				return true;
			}
		});
		return true;
	}

	public void hideSearchView() {
		searchMenuItem.collapseActionView();
		wikiViewer.requestFocus(); //pass control back to the wikiview
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(@NonNull MenuItem item) {
		switch (item.getItemId()) {
			case R.id.menu_share:
				WikiPage wikiPage = wikiViewer.getCurrentWebPage();
				if (wikiPage != null) {
					Intent sharingIntent = new Intent();
					sharingIntent.setType(TEXT_PLAIN_MIME);
					sharingIntent.setAction(Intent.ACTION_SEND);
					sharingIntent.putExtra(Intent.EXTRA_TITLE, wikiPage.getPageTitle());
					sharingIntent.putExtra(Intent.EXTRA_TEXT, wikiPage.getPageUrl());
					startActivity(Intent.createChooser(sharingIntent, null));
				}
				break;
			case R.id.refresh:
				wikiViewer.onRefresh();
				break;
			case R.id.menu_settings:
				startActivity(new Intent(this, PreferencesActivity.class));
				break;
			case R.id.exit:
				finish();
				break;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onFinish(List<SearchResult> results) {
		currentSuggestions = results;
		setCursorAdapter(currentSuggestions);
	}

	private void setCursorAdapter(List<SearchResult> currentSuggestions) {
		searchView.setSuggestionsAdapter(
				SearchResultsAdapter.getCursorAdapter(this, currentSuggestions)
		);
	}
}