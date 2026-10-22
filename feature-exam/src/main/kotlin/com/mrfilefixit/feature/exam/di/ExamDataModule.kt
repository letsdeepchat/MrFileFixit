package com.mrfilefixit.feature.exam.di

import com.mrfilefixit.core.domain.exam.ExamRulesRepository
import com.mrfilefixit.feature.exam.data.AssetExamRulesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal interface ExamDataModule {
    @Binds
    fun bindExamRulesRepository(impl: AssetExamRulesRepository): ExamRulesRepository
}
