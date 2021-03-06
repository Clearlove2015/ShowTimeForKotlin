package com.example.happyghost.showtimeforkotlin.inject.component.bookcomponent

import com.example.happyghost.showtimeforkotlin.inject.PerActivity
import com.example.happyghost.showtimeforkotlin.inject.component.ApplicationComponent
import com.example.happyghost.showtimeforkotlin.inject.module.bookmodule.ChannelModule
import com.example.happyghost.showtimeforkotlin.ui.news.channel.ChannelActivity
import dagger.Component

/**
 * @author Zhao Chenping
 * @creat 2017/9/4.
 * @description
 */
@PerActivity
@Component(dependencies = arrayOf(ApplicationComponent::class),modules = arrayOf(ChannelModule::class))
interface ChannelComponent {
    fun inject(channelActivity: ChannelActivity)
}