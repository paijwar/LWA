package com.kinglloy.album.data.repository

import android.content.Context
import com.kinglloy.album.data.entity.mapper.WallpaperEntityMapper
import com.kinglloy.album.data.repository.datasource.WallpaperDataStoreFactory
import com.kinglloy.album.data.repository.datasource.sync.SyncHelper
import com.kinglloy.album.data.repository.datasource.sync.account.Account
import com.kinglloy.album.domain.Wallpaper
import com.kinglloy.album.domain.repository.WallpaperRepository
import io.reactivex.Observable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * @author jinyalin
 * @since 2017/7/27.
 */
@Singleton
class AdvanceWallpaperDataRepository
@Inject constructor(val context: Context,
                    val factory: WallpaperDataStoreFactory,
                    private val wallpaperMapper: WallpaperEntityMapper)
    : WallpaperRepository {
    init {
        Account.createSyncAccount(context)
        SyncHelper.updateSyncInterval(context)
    }

    override fun getLiveWallpapers(): Observable<List<Wallpaper>> =
            factory.createLiveDataStore().getWallpaperEntities().map(wallpaperMapper::transformList)

    override fun getStyleWallpapers(): Observable<MutableList<Wallpaper>> =
            factory.createStyleDataStore().getWallpaperEntities().map(wallpaperMapper::transformList)

    override fun loadLiveWallpapers(): Observable<List<Wallpaper>> {
        return factory.createRemoteLiveDataStore().getWallpaperEntities()
                .map(wallpaperMapper::transformList)
    }

    override fun loadStyleWallpapers(): Observable<MutableList<Wallpaper>> {
        return factory.createRemoteStyleDataStore().getWallpaperEntities()
                .map(wallpaperMapper::transformList)
    }

    override fun downloadLiveWallpaper(wallpaperId: String): Observable<Long> =
            factory.createRemoteLiveDataStore().downloadWallpaper(wallpaperId)

    override fun downloadStyleWallpaper(wallpaperId: String): Observable<Long> =
            factory.createRemoteStyleDataStore().downloadWallpaper(wallpaperId)

    override fun selectPreviewingLiveWallpaper():
            Observable<Boolean> = factory.createLiveDataStore().selectPreviewingWallpaper()

    override fun selectPreviewingStyleWallpaper():
            Observable<Boolean> = factory.createStyleDataStore().selectPreviewingWallpaper()

    override fun previewLiveWallpaper(wallpaperId: String): Observable<Boolean> =
            factory.createLiveDataStore().previewWallpaper(wallpaperId)

    override fun previewStyleWallpaper(wallpaperId: String): Observable<Boolean> =
            factory.createStyleDataStore().previewWallpaper(wallpaperId)

    override fun getPreviewLiveWallpaper(): Wallpaper =
            wallpaperMapper.transform(factory.createLiveDataStore().getPreviewWallpaperEntity())

    override fun activeService(serviceType: Int): Observable<Boolean>
            = factory.createLiveDataStore().activeService(serviceType)

    override fun getActiveService(): Observable<Int> = factory.createLiveDataStore().getActiveService()
}