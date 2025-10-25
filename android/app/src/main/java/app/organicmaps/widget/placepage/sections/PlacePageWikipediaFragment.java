package app.organicmaps.widget.placepage.sections;

import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import app.organicmaps.R;
import app.organicmaps.sdk.bookmarks.data.MapObject;
import app.organicmaps.sdk.bookmarks.data.Metadata;
import app.organicmaps.util.UiUtils;
import app.organicmaps.util.Utils;
import app.organicmaps.widget.placepage.PlacePageUtils;
import app.organicmaps.widget.placepage.PlacePageViewModel;
import app.organicmaps.widget.placepage.WikiArticleActivity;

public class PlacePageWikipediaFragment extends Fragment implements Observer<MapObject>
{
  private View mFrame;
  private View mWiki;
  private View mWikiArticleViewContainer;

  private TextView mWikiArticleView;

  private PlacePageViewModel mViewModel;

  private MapObject mMapObject;

  private int mWikiArticleMaxLength;

  @Nullable
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                           @Nullable Bundle savedInstanceState)
  {
    mViewModel = new ViewModelProvider(requireActivity()).get(PlacePageViewModel.class);
    return inflater.inflate(R.layout.place_page_wikipedia_fragment, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState)
  {
    super.onViewCreated(view, savedInstanceState);
    mWikiArticleMaxLength = getResources().getInteger(R.integer.place_page_wiki_article_max_length);

    mFrame = view;

    mWikiArticleView = view.findViewById(R.id.poi_wiki_article);
    View wikiArticleMoreBtn = view.findViewById(R.id.more_btn);
    mWikiArticleViewContainer = view.findViewById(R.id.poi_wiki_article_container);
    // Wikipedia functionality disabled
    // wikiArticleMoreBtn.setOnClickListener(v -> showWikiArticleScreen());
    // mWikiArticleView.setOnClickListener(v -> showWikiArticleScreen());
    mWiki = view.findViewById(R.id.ll__place_wiki);
  }

  private void showWikiArticleScreen()
  {
    WikiArticleActivity.start(requireContext(), mMapObject.getName(), mMapObject.getWikiArticle());
  }

  private Spanned getShortWikiArticle()
  {
    String htmlWikiArticle = mMapObject.getWikiArticle();
    final int paragraphStart = htmlWikiArticle.indexOf("<p>");
    final int paragraphEnd = htmlWikiArticle.indexOf("</p>");
    if (paragraphStart == 0 && paragraphEnd != -1)
      htmlWikiArticle = htmlWikiArticle.substring(3, paragraphEnd);

    Spanned wikiArticle = Utils.fromHtml(htmlWikiArticle);
    if (wikiArticle.length() > mWikiArticleMaxLength)
    {
      wikiArticle = (Spanned) new SpannableStringBuilder(wikiArticle)
                        .insert(mWikiArticleMaxLength - 3, "...")
                        .subSequence(0, mWikiArticleMaxLength);
    }

    return wikiArticle;
  }

  private void updateViews()
  {
    // Wikipedia functionality disabled - hide everything
    UiUtils.hide(mWikiArticleViewContainer);
    UiUtils.hide(mWiki);
  }

  @Override
  public void onStart()
  {
    super.onStart();
    mViewModel.getMapObject().observe(requireActivity(), this);
  }

  @Override
  public void onStop()
  {
    super.onStop();
    mViewModel.getMapObject().removeObserver(this);
  }

  @Override
  public void onChanged(@Nullable MapObject mapObject)
  {
    if (mapObject != null)
    {
      mMapObject = mapObject;
      updateViews();
    }
  }
}
