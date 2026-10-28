package com.paopao.calendar.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.paopao.calendar.AppContainer

/**
 * ViewModel 工厂。
 *
 * 容器从调用方闭包捕获而不是从 `CreationExtras` 里取 APPLICATION_KEY：
 * 后者依赖 lifecycle 内部键的可见性，版本升级时容易断，而这个工厂的消费者全是
 * 各自屏幕里的 `viewModel(factory = …)`，没有跨模块共享的需求。
 */
inline fun <reified VM : ViewModel> appViewModelFactory(
    container: AppContainer,
    crossinline create: (AppContainer) -> VM,
): ViewModelProvider.Factory = viewModelFactory {
    initializer { create(container) }
}
