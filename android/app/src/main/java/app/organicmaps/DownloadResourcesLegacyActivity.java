package app.organicmaps;

import static app.organicmaps.sdk.DownloadResourcesLegacyActivity.ERR_DISK_ERROR;
import static app.organicmaps.sdk.DownloadResourcesLegacyActivity.ERR_DOWNLOAD_ERROR;
import static app.organicmaps.sdk.DownloadResourcesLegacyActivity.ERR_DOWNLOAD_SUCCESS;
import static app.organicmaps.sdk.DownloadResourcesLegacyActivity.ERR_NOT_ENOUGH_FREE_SPACE;
import static app.organicmaps.sdk.DownloadResourcesLegacyActivity.ERR_NO_MORE_FILES;
import static app.organicmaps.sdk.DownloadResourcesLegacyActivity.ERR_STORAGE_DISCONNECTED;
import static app.organicmaps.sdk.DownloadResourcesLegacyActivity.nativeCancelCurrentFile;
import static app.organicmaps.sdk.DownloadResourcesLegacyActivity.nativeGetBytesToDownload;
import static app.organicmaps.sdk.DownloadResourcesLegacyActivity.nativeStartNextFileDownload;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Intent;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.view.ViewCompat;
import app.organicmaps.base.BaseMwmFragmentActivity;
import app.organicmaps.downloader.MapManagerHelper;
import app.organicmaps.intent.Factory;
import app.organicmaps.sdk.Framework;
import app.organicmaps.sdk.downloader.CountryItem;
import app.organicmaps.sdk.downloader.MapManager;
import app.organicmaps.sdk.location.LocationListener;
import app.organicmaps.sdk.util.Config;
import app.organicmaps.sdk.util.ConnectionState;
import app.organicmaps.sdk.util.StringUtils;
import app.organicmaps.util.UiUtils;
import app.organicmaps.util.Utils;
import app.organicmaps.util.WindowInsetUtils.PaddingInsetsListener;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import java.util.List;
import java.util.Objects;

@SuppressLint("StringFormatMatches")
public class DownloadResourcesLegacyActivity extends BaseMwmFragmentActivity
{
  private static final String TAG = DownloadResourcesLegacyActivity.class.getSimpleName();

  private TextView mTvMessage;
  private TextView mTvHeadMessage;
  private ImageView mDownloadIcon;
  private LinearProgressIndicator mProgress;

  private String mCurrentCountry;

  @Nullable
  private Dialog mAlertDialog;

  @NonNull
  private ActivityResultLauncher<Intent> mApiRequest;

  private boolean mAreResourcesDownloaded;

  private int mCountryDownloadListenerSlot;

  private ConnectivityManager.NetworkCallback mNetworkCallback;

  private final LocationListener mLocationListener = new LocationListener() {
    @Override
    public void onLocationUpdated(@NonNull Location location)
    {
      if (mCurrentCountry != null)
        return;

      final double lat = location.getLatitude();
      final double lon = location.getLongitude();
      mCurrentCountry = MapManager.nativeFindCountry(lat, lon);
      if (TextUtils.isEmpty(mCurrentCountry))
      {
        mCurrentCountry = null;
        return;
      }

      // Location detected - will automatically download local map after world map completes
      MwmApplication.from(DownloadResourcesLegacyActivity.this).getLocationHelper().removeListener(this);
    }
  };

  private final app.organicmaps.sdk.DownloadResourcesLegacyActivity.Listener mResourcesDownloadListener =
      new app.organicmaps.sdk.DownloadResourcesLegacyActivity.Listener() {
        @Override
        public void onProgress(final int percent)
        {
          if (!isFinishing())
            mProgress.setProgressCompat(percent, true);
        }

        @Override
        public void onFinish(final int errorCode)
        {
          if (isFinishing())
            return;

          if (errorCode == ERR_DOWNLOAD_SUCCESS)
          {
            final int res = nativeStartNextFileDownload(mResourcesDownloadListener);
            if (res == ERR_NO_MORE_FILES)
              finishFilesDownload(res);
          }
          else
            finishFilesDownload(errorCode);
        }
      };

  private final MapManager.StorageCallback mCountryDownloadListener = new MapManager.StorageCallback() {
    @Override
    public void onStatusChanged(List<MapManager.StorageCallbackData> data)
    {
      for (MapManager.StorageCallbackData item : data)
      {
        if (!item.isLeafNode)
          continue;

        switch (item.newStatus)
        {
        case CountryItem.STATUS_DONE:
          mAreResourcesDownloaded = true;
          showMap();
          return;

        case CountryItem.STATUS_FAILED:
          MapManagerHelper.showError(DownloadResourcesLegacyActivity.this, item, null);
          return;
        }
      }
    }

    @Override
    public void onProgress(String countryId, long localSize, long remoteSize)
    {
      mProgress.setProgressCompat((int) localSize, true);
    }
  };

  @CallSuper
  @Override
  protected void onSafeCreate(@Nullable Bundle savedInstanceState)
  {
    super.onSafeCreate(savedInstanceState);
    UiUtils.setLightStatusBar(this, true);
    setContentView(R.layout.activity_download_resources);
    final View view = getWindow().getDecorView().findViewById(android.R.id.content);
    ViewCompat.setOnApplyWindowInsetsListener(view, PaddingInsetsListener.allSides());
    initViewsAndListeners();
    mApiRequest = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
      setResult(result.getResultCode(), result.getData());
      finish();
    });

    if (prepareFilesDownload(false))
    {
      Utils.keepScreenOn(true, getWindow());

      // Auto-start download if on WiFi, otherwise show "connect to WiFi" message
      if (canDownload())
      {
        // WiFi available - show downloading UI with globe icon (world map)
        mDownloadIcon.setImageResource(R.drawable.ic_globe);
        mDownloadIcon.setColorFilter(ContextCompat.getColor(this, R.color.base_red));
        mTvHeadMessage.setText(R.string.downloading_world_map);
        mTvMessage.setText(""); // Clear subtitle during download
        doDownload();
      }
      else
      {
        // No WiFi - show connect to WiFi UI
        mDownloadIcon.setImageResource(R.drawable.ic_wifi_off);
        mDownloadIcon.setColorFilter(ContextCompat.getColor(this, R.color.base_red));
        mTvHeadMessage.setText(R.string.connect_to_wifi_title);
        mTvMessage.setText(R.string.connect_to_wifi_message);
      }

      return;
    }

    showMap();
  }

  @CallSuper
  @Override
  protected void onSafeDestroy()
  {
    super.onSafeDestroy();
    mApiRequest.unregister();
    mApiRequest = null;
    Utils.keepScreenOn(Config.isKeepScreenOnEnabled(), getWindow());
    if (mCountryDownloadListenerSlot != 0)
    {
      MapManager.nativeUnsubscribe(mCountryDownloadListenerSlot);
      mCountryDownloadListenerSlot = 0;
    }
  }

  @CallSuper
  @Override
  protected void onResume()
  {
    super.onResume();
    if (!isFinishing())
      MwmApplication.from(this).getLocationHelper().addListener(mLocationListener);

    // Auto-start download if we're waiting and WiFi is now available
    if (!mAreResourcesDownloaded && canDownload())
    {
      // WiFi now available - update UI and start download with globe icon
      mDownloadIcon.setImageResource(R.drawable.ic_globe);
      mDownloadIcon.setColorFilter(ContextCompat.getColor(this, R.color.base_red));
      mTvHeadMessage.setText(R.string.downloading_world_map);
      mTvMessage.setText(""); // Clear subtitle during download
      doDownload();
    }

    // Register network callback to detect when WiFi becomes available
    if (!mAreResourcesDownloaded && mNetworkCallback == null)
    {
      final ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
      if (connectivityManager != null)
      {
        mNetworkCallback = new ConnectivityManager.NetworkCallback() {
          @Override
          public void onAvailable(@NonNull Network network)
          {
            // Check if this is a WiFi connection
            final NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
            if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
            {
              // WiFi is now available - update UI and start download on main thread
              runOnUiThread(() -> {
                if (!mAreResourcesDownloaded && !isFinishing())
                {
                  mDownloadIcon.setImageResource(R.drawable.ic_globe);
                  mDownloadIcon.setColorFilter(ContextCompat.getColor(DownloadResourcesLegacyActivity.this, R.color.base_red));
                  mTvHeadMessage.setText(R.string.downloading_world_map);
                  mTvMessage.setText(""); // Clear subtitle during download
                  doDownload();
                }
              });
            }
          }
        };

        final NetworkRequest request = new NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build();
        connectivityManager.registerNetworkCallback(request, mNetworkCallback);
      }
    }
  }

  @Override
  protected void onPause()
  {
    super.onPause();
    MwmApplication.from(this).getLocationHelper().removeListener(mLocationListener);
    if (mAlertDialog != null && mAlertDialog.isShowing())
      mAlertDialog.dismiss();
    mAlertDialog = null;

    // Unregister network callback to avoid leaks
    if (mNetworkCallback != null)
    {
      final ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
      if (connectivityManager != null)
      {
        connectivityManager.unregisterNetworkCallback(mNetworkCallback);
      }
      mNetworkCallback = null;
    }
  }

  private void setDownloadMessage(int bytesToDownload)
  {
    mTvMessage.setText(getString(R.string.download_resources, StringUtils.getFileSizeString(this, bytesToDownload)));
  }

  /**
   * Check if download can proceed based on WiFi-only setting and connection status
   */
  private boolean canDownload()
  {
    // If WiFi-only is disabled, allow download on any connection
    if (!Config.isWifiOnlyDownloadsEnabled())
      return true;

    // If WiFi-only is enabled, only allow download on WiFi
    return ConnectionState.INSTANCE.isWifiConnected();
  }

  private boolean prepareFilesDownload(boolean showMap)
  {
    final int bytes = nativeGetBytesToDownload();
    if (bytes == 0)
    {
      mAreResourcesDownloaded = true;
      if (showMap)
        showMap();

      return false;
    }

    if (bytes > 0)
    {
      setDownloadMessage(bytes);

      mProgress.setMax(bytes);
      mProgress.setProgressCompat(0, true);
    }
    else
      finishFilesDownload(bytes);

    return true;
  }

  private void initViewsAndListeners()
  {
    mTvMessage = findViewById(R.id.download_message);
    mTvHeadMessage = findViewById(R.id.head_message);
    mDownloadIcon = findViewById(R.id.download_icon);
    mProgress = findViewById(R.id.progressbar);
  }

  private void doDownload()
  {
    if (nativeStartNextFileDownload(mResourcesDownloadListener) == ERR_NO_MORE_FILES)
      finishFilesDownload(ERR_NO_MORE_FILES);
  }

  public void showMap()
  {
    if (!mAreResourcesDownloaded)
      return;

    // Re-use original intent to retain all flags and payload.
    // https://github.com/organicmaps/organicmaps/issues/6944
    final Intent intent = Objects.requireNonNull(getIntent());
    intent.setComponent(new ComponentName(this, MwmActivity.class));

    // Disable animation because MwmActivity should appear exactly over this one
    intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_CLEAR_TOP);

    // See {@link SplashActivity.processNavigation()}
    if (Factory.isStartedForApiResult(intent))
    {
      // Wait for the result from MwmActivity for API callers.
      mApiRequest.launch(intent);
      return;
    }

    startActivity(intent);
    finish();
  }

  private void finishFilesDownload(int result)
  {
    if (result == ERR_NO_MORE_FILES)
    {
      // World and WorldCoasts has been downloaded, we should register maps again to correctly add them to the model.
      Framework.nativeReloadWorldMaps();

      // Automatically download the local country map if detected
      if (mCurrentCountry != null)
      {
        // Switch icon to map_search for local map download
        mDownloadIcon.setImageResource(R.drawable.ic_map_search);
        mDownloadIcon.setColorFilter(ContextCompat.getColor(this, R.color.base_red));
        mTvHeadMessage.setText(R.string.downloading_local_map);

        CountryItem item = CountryItem.fill(mCurrentCountry);
        mTvMessage.setText(item.name);
        mProgress.setMax((int) item.totalSize);
        mProgress.setProgressCompat(0, true);

        mCountryDownloadListenerSlot = MapManager.nativeSubscribe(mCountryDownloadListener);
        // Use warn3gAndDownload to check WiFi-only setting
        MapManagerHelper.warn3gAndDownload(this, mCurrentCountry, null);
      }
      else
      {
        mAreResourcesDownloaded = true;
        showMap();
      }
    }
    else
    {
      showErrorDialog(result);
    }
  }

  private void showErrorDialog(int result)
  {
    if (mAlertDialog != null && mAlertDialog.isShowing())
      return;

    @StringRes
    final int titleId;
    @StringRes
    final int messageId = switch (result)
    {
      case ERR_NOT_ENOUGH_FREE_SPACE ->
      {
        titleId = R.string.routing_not_enough_space;
        yield R.string.not_enough_free_space_on_sdcard;
      }
      case ERR_STORAGE_DISCONNECTED ->
      {
        titleId = R.string.disconnect_usb_cable_title;
        yield R.string.disconnect_usb_cable;
      }
      case ERR_DOWNLOAD_ERROR ->
      {
        titleId = R.string.connection_failure;
        yield(ConnectionState.INSTANCE.isConnected() ? R.string.download_has_failed
                                                     : R.string.common_check_internet_connection_dialog);
      }
      case ERR_DISK_ERROR ->
      {
        titleId = R.string.disk_error_title;
        yield R.string.disk_error;
      }
      default -> throw new AssertionError("Unexpected result code = " + result);
    };

    mAlertDialog = new MaterialAlertDialogBuilder(this, R.style.MwmTheme_AlertDialog)
                       .setTitle(titleId)
                       .setMessage(messageId)
                       .setCancelable(true)
                       .setPositiveButton(R.string.ok, null)
                       .setOnDismissListener(dialog -> mAlertDialog = null)
                       .show();
  }
}
