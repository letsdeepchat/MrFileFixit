package com.mrfilefixit.core.ads.di

import com.mrfilefixit.core.ads.AdManager
import com.mrfilefixit.core.ui.ads.AdSlots
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class AdsModule {
    @Binds
    abstract fun bindAdSlots(manager: AdManager): AdSlots
}
