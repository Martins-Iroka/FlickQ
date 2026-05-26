package com.martdev.flickq

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module

@Module
@ComponentScan("com.martdev.flickq.**")
@Configuration
class AppModule