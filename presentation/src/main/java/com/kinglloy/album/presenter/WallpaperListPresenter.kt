package com.kinglloy.album.presenter

import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.text.TextUtils
import com.kinglloy.album.WallpaperSwitcher
import com.kinglloy.album.analytics.Analytics
import com.kinglloy.album.analytics.Event
import com.kinglloy.album.data.exception.NetworkConnectionException
import com.kinglloy.album.data.log.LogUtil
import com.kinglloy.album.data.repository.datasource.provider.AlbumContract
import com.kinglloy.album.data.utils.WallpaperFileHelper
import com.kinglloy.album.domain.Wallpaper
import com.kinglloy.album.domain.WallpaperType
import com.kinglloy.album.domain.WallpaperType.HD
import com.kinglloy.album.domain.WallpaperType.LIVE
import com.kinglloy.album.domain.WallpaperType.STYLE
import com.kinglloy.album.domain.WallpaperType.VIDEO
import com.kinglloy.album.domain.interactor.*
import com.kinglloy.album.exception.ErrorMessageFactory
import com.kinglloy.album.mapper.WallpaperItemMapper
import com.kinglloy.album.model.WallpaperItem
import com.kinglloy.album.view.WallpaperListView
import com.kinglloy.download.DownloadListener
import com.kinglloy.download.KinglloyDownloader
import com.kinglloy.download.exceptions.ErrorCode
import com.kinglloy.download.state.DownloadState
import javax.inject.Inject

/**
 * @author jinyalin
 * @since 2017/10/31.
 */
class WallpaperListPresenter
@Inject constructor(
  private val getWallpapers: GetWallpapers,
  private val loadWallpaper: LoadWallpaper,
  private val previewWallpaper: PreviewWallpaper,
  val wallpaperItemMapper: WallpaperItemMapper,
  val wallpaperSwitcher: WallpaperSwitcher,
  val getPreviewWallpaper: GetPreviewWallpaper
) : Presenter, DownloadListener {

  companion object {
    const val TAG = "WallpaperListPresenter"
    const val DOWNLOAD_STATE = "download_state"
    const val DOWNLOADING_ITEM = "download_item"

    const val DOWNLOAD_NONE = 0
    const val DOWNLOADING = 1
    const val DOWNLOAD_ERROR = 2

    private var currentPreviewing: WallpaperItem? = null
  }

  private var view: WallpaperListView? = null

  private var currentDownloadId: Long = -1
  private var downloadingWallpaper: WallpaperItem? = null
  private var wallpapers: List<WallpaperItem>? = null

  private var downloadState = DOWNLOAD_NONE

  private val mContentObserver = object : ContentObserver(Handler()) {
    override fun onChange(selfChange: Boolean, uri: Uri) {
      LogUtil.D(TAG, "Uri change.$uri")
      if (uri == AlbumContract.LiveWallpaper.CONTENT_SELECT_PREVIEWING_URI ||
        uri == AlbumContract.VideoWallpaper.CONTENT_SELECT_PREVIEWING_URI ||
        uri == AlbumContract.StyleWallpaper.CONTENT_SELECT_PREVIEWING_URI ||
        uri == AlbumContract.HDWallpaper.CONTENT_SELECT_PREVIEWING_URI
      ) {
        view?.selectWallpaper(
          wallpaperItemMapper
            .transform(getPreviewWallpaper.previewing)
        )
      } else if (uri == AlbumContract.LiveWallpaper.CONTENT_UNSELECT_URI ||
        uri == AlbumContract.VideoWallpaper.CONTENT_UNSELECT_URI ||
        uri == AlbumContract.StyleWallpaper.CONTENT_UNSELECT_URI ||
        uri == AlbumContract.HDWallpaper.CONTENT_UNSELECT_URI
      ) {
        val item = wallpapers?.find {
          it.isSelected
        }
        item?.apply {
          view?.unSelectWallpaper(this)
        }
      } else if (uri == AlbumContract.LiveWallpaper.CONTENT_URI ||
        uri == AlbumContract.VideoWallpaper.CONTENT_URI ||
        uri == AlbumContract.StyleWallpaper.CONTENT_URI ||
        uri == AlbumContract.HDWallpaper.CONTENT_URI
      ) {
        view?.apply {
          when (getWallpaperType()) {
            LIVE -> {
              if (uri == AlbumContract.LiveWallpaper.CONTENT_URI) {
                initialize(LIVE)
              }
            }
            STYLE -> {
              if (uri == AlbumContract.StyleWallpaper.CONTENT_URI) {
                initialize(STYLE)
              }
            }
            HD -> {
              if (uri == AlbumContract.HDWallpaper.CONTENT_URI) {
                initialize(HD)
              }
            }
            VIDEO -> {
              if (uri == AlbumContract.VideoWallpaper.CONTENT_URI) {
                initialize(VIDEO)
              }
            }
          }
        }
      }
    }
  }

  private val mDownloadItemDeletedObserver = object : ContentObserver(Handler()) {
    override fun onChange(selfChange: Boolean, uri: Uri) {
      try {
        val wallpaperId: String = when (view!!.getWallpaperType()) {
          WallpaperType.VIDEO -> {
            AlbumContract.VideoWallpaper.getDeletedWallpaperId(uri)
          }
          WallpaperType.LIVE -> {
            AlbumContract.LiveWallpaper.getDeletedWallpaperId(uri)
          }
          WallpaperType.HD -> {
            AlbumContract.HDWallpaper.getDeletedWallpaperId(uri)
          }
          else -> {
            AlbumContract.StyleWallpaper.getDeletedWallpaperId(uri)
          }
        }
        view?.deletedDownloadWallpaper(wallpaperId)
      } catch (ignore: Exception) {
        // maybe notify by it's ancestors
      }
    }
  }

  fun setView(view: WallpaperListView) {
    this.view = view

    when {
      view.getWallpaperType() == WallpaperType.LIVE -> {
        view.context().contentResolver.registerContentObserver(
          AlbumContract.LiveWallpaper.CONTENT_SELECT_PREVIEWING_URI,
          true, mContentObserver
        )
        view.context().contentResolver.registerContentObserver(
          AlbumContract.LiveWallpaper.CONTENT_UNSELECT_URI,
          true, mContentObserver
        )
        view.context().contentResolver.registerContentObserver(
          AlbumContract.LiveWallpaper.CONTENT_URI,
          true, mContentObserver
        )
        view.context().contentResolver.registerContentObserver(
          AlbumContract.LiveWallpaper.CONTENT_DOWNLOAD_ITEM_DELETED_URI,
          true, mDownloadItemDeletedObserver
        )
      }
      view.getWallpaperType() == WallpaperType.STYLE -> {
        view.context().contentResolver.registerContentObserver(
          AlbumContract.StyleWallpaper.CONTENT_SELECT_PREVIEWING_URI,
          true, mContentObserver
        )
        view.context().contentResolver.registerContentObserver(
          AlbumContract.StyleWallpaper.CONTENT_UNSELECT_URI,
          true, mContentObserver
        )
        view.context().contentResolver.registerContentObserver(
          AlbumContract.StyleWallpaper.CONTENT_URI,
          true, mContentObserver
        )
        view.context().contentResolver.registerContentObserver(
          AlbumContract.StyleWallpaper.CONTENT_DOWNLOAD_ITEM_DELETED_URI,
          true, mDownloadItemDeletedObserver
        )
      }
      view.getWallpaperType() == WallpaperType.VIDEO -> {
        view.context().contentResolver.registerContentObserver(
          AlbumContract.VideoWallpaper.CONTENT_SELECT_PREVIEWING_URI,
          true, mContentObserver
        )
        view.context().contentResolver.registerContentObserver(
          AlbumContract.VideoWallpaper.CONTENT_UNSELECT_URI,
          true, mContentObserver
        )
        view.context().contentResolver.registerContentObserver(
          AlbumContract.VideoWallpaper.CONTENT_URI,
          true, mContentObserver
        )
        view.context().contentResolver.registerContentObserver(
          AlbumContract.VideoWallpaper.CONTENT_DOWNLOAD_ITEM_DELETED_URI,
          true, mDownloadItemDeletedObserver
        )
      }
      else -> {
        view.context().contentResolver.registerContentObserver(
          AlbumContract.HDWallpaper.CONTENT_SELECT_PREVIEWING_URI,
          true, mContentObserver
        )
        view.context().contentResolver.registerContentObserver(
          AlbumContract.HDWallpaper.CONTENT_UNSELECT_URI,
          true, mContentObserver
        )
        view.context().contentResolver.registerContentObserver(
          AlbumContract.HDWallpaper.CONTENT_URI,
          true, mContentObserver
        )
        view.context().contentResolver.registerContentObserver(
          AlbumContract.HDWallpaper.CONTENT_DOWNLOAD_ITEM_DELETED_URI,
          true, mDownloadItemDeletedObserver
        )
      }
    }

  }

  fun initialize(wallpaperType: WallpaperType) {
    view?.showLoading()
    getWallpapers.execute(
      WallpapersObserver(),
      GetWallpapers.Params.withType(wallpaperType)
    )
  }

  fun loadWallpapers(type: WallpaperType) {
    view?.showLoading()
    loadWallpaper.execute(object : DefaultObserver<List<Wallpaper>>() {
      override fun onNext(needDownload: List<Wallpaper>) {
        wallpapers = wallpaperItemMapper.transformList(needDownload)
        view?.renderWallpapers(wallpapers!!)
      }

      override fun onComplete() {

      }

      override fun onError(exception: Throwable) {
        view?.showError(
          ErrorMessageFactory.create(view!!.context(), exception as Exception)
        )
        view?.showRetry()
      }
    }, LoadWallpaper.Params.withType(type))
  }

  fun previewWallpaper(item: WallpaperItem) {
    if (WallpaperFileHelper.isNeedDownloadWallpaper(
        item.lazyDownload,
        item.storePath
      ) || (downloadingWallpaper != null
          && TextUtils.equals(downloadingWallpaper!!.wallpaperId, item.wallpaperId))
    ) {
      view?.showDownloadHintDialog(item)
    } else {
      previewWallpaper.execute(object : DefaultObserver<Boolean>() {
        override fun onNext(success: Boolean) {
          currentPreviewing = item
          wallpaperSwitcher.switchService(view!!.context())
        }
      }, PreviewWallpaper.Params.previewWallpaper(item.wallpaperId, item.wallpaperType))
    }
  }

  fun requestDownload(item: WallpaperItem) {
    view?.apply {
      val bundle = Bundle()
      bundle.putString(Event.WallpaperItem.ITEM_NAME, item.name)
      bundle.putString(Event.WallpaperItem.ITEM_TYPE, item.wallpaperType.name)
      bundle.putString(Event.WallpaperItem.ITEM_PROVIDER_NAME, item.providerName)
      Analytics.logEvent(context(), Event.DOWNLOAD_WALLPAPER, bundle)

      showDownloadingDialog(item)
      downloadingWallpaper = item
      downloadState = DOWNLOADING
      val downloader = KinglloyDownloader.getInstance(context())
      val downloadRequest = KinglloyDownloader.Request(item.downloadUrl)
        .setDestinationPath(item.storePath)
      val downloadId = downloader.queryId(downloadRequest)
      if (downloader.getState(downloadId) == DownloadState.STATE_DOWNLOADING) {
        currentDownloadId = downloadId
      } else {
        if (downloadId != -1L) {
          currentDownloadId = downloadId
          downloader.start(currentDownloadId)
        } else {
          currentDownloadId = KinglloyDownloader.getInstance(context())
            .enqueue(downloadRequest)
        }
      }

      KinglloyDownloader.getInstance(context())
        .registerListener(currentDownloadId, this@WallpaperListPresenter)
    }
  }

  fun cancelCurrentDownload() {
    KinglloyDownloader.getInstance(view!!.context()).cancel(currentDownloadId)
  }

  fun onSaveInstanceState(outState: Bundle) {
    outState.putInt(DOWNLOAD_STATE, downloadState)
    if (downloadingWallpaper != null) {
      outState.putParcelable(DOWNLOADING_ITEM, downloadingWallpaper!!)
    }
  }

  fun onRestoreInstanceState(savedInstanceState: Bundle) {
    downloadState = savedInstanceState.getInt(DOWNLOAD_STATE)
    downloadingWallpaper = savedInstanceState.getParcelable(DOWNLOADING_ITEM)

    if (downloadingWallpaper != null) {
      if (downloadState == DOWNLOADING) {
        requestDownload(downloadingWallpaper!!)
      } else if (downloadState == DOWNLOAD_ERROR) {

      }
    }
  }

  fun getDownloadingItem(): WallpaperItem? = downloadingWallpaper

  override fun resume() {
  }

  override fun pause() {

  }

  override fun destroy() {
    view?.apply {
      KinglloyDownloader.getInstance(context())
        .unregisterListener(currentDownloadId, this@WallpaperListPresenter)
      context().contentResolver?.unregisterContentObserver(mContentObserver)
      context().contentResolver?.unregisterContentObserver(mDownloadItemDeletedObserver)
    }

    getWallpapers.dispose()
    loadWallpaper.dispose()
    downloadingWallpaper = null
    view = null
  }

  override fun onDownloadPending(downloadId: Long) {

  }

  override fun onDownloadProgress(downloadId: Long, downloadedSize: Long, totalSize: Long) {
    view?.updateDownloadingProgress(downloadedSize)
  }

  override fun onDownloadPause(downloadId: Long) {

  }

  override fun onDownloadComplete(downloadId: Long, path: String?) {
    view?.apply {
      if (downloadingWallpaper != null) {
        downloadComplete(downloadingWallpaper!!)
      }
      KinglloyDownloader.getInstance(context())
        .unregisterListener(downloadId, this@WallpaperListPresenter)
    }
    downloadingWallpaper = null
    currentDownloadId = -1
    downloadState = DOWNLOAD_NONE
  }

  override fun onDownloadError(downloadId: Long, errorCode: Int, errorMessage: String?) {
    view?.apply {
      if (errorCode == ErrorCode.ERROR_CONNECT_TIMEOUT) {
        showDownloadError(downloadingWallpaper!!, NetworkConnectionException())
      } else {
        showDownloadError(downloadingWallpaper!!, Exception(errorMessage))
      }
      KinglloyDownloader.getInstance(context())
        .unregisterListener(downloadId, this@WallpaperListPresenter)
    }

    downloadingWallpaper = null
    currentDownloadId = -1
    downloadState = DOWNLOAD_ERROR
  }

  private inner class WallpapersObserver : DefaultObserver<List<Wallpaper>>() {
    override fun onNext(needDownload: List<Wallpaper>) {
      if (needDownload.isEmpty()) {
        view?.showEmpty()
      } else {
        wallpapers = wallpaperItemMapper.transformList(needDownload)
        view?.renderWallpapers(wallpapers!!)
      }
    }

    override fun onComplete() {
    }

    override fun onError(exception: Throwable?) {
      view?.showEmpty()
    }
  }
}