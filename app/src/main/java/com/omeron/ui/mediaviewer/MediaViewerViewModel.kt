package com.omeron.ui.mediaviewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omeron.data.model.GalleryMedia
import com.omeron.data.model.MediaType
import com.omeron.data.model.Resource
import com.omeron.data.repository.PreferencesRepository
import com.omeron.util.extension.updateValue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class MediaViewerViewModel
@Inject constructor(
    private val mediaResolver: MediaResolver,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val _media: MutableStateFlow<Resource<List<GalleryMedia>>> =
        MutableStateFlow(Resource.Loading())
    val media: StateFlow<Resource<List<GalleryMedia>>> = _media

    private val _selectedPage: MutableStateFlow<Int> = MutableStateFlow(0)
    val selectedPage: StateFlow<Int> = _selectedPage

    val isMultiMedia: StateFlow<Boolean> = _media
        .filter { it is Resource.Success }
        .map { (it as Resource.Success).data.size > 1 }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val isVideoMuted: Flow<Boolean>
        get() = preferencesRepository.getMuteVideo(false)

    var hideControls: Boolean = false

    init {
        viewModelScope.launch { preferencesRepository.getMuteVideo(false).first() }
    }

    fun loadMedia(link: String, mediaType: MediaType, forceUpdate: Boolean = false) {
        if (_media.value !is Resource.Success || forceUpdate) {
            viewModelScope.launch { retrieveMedia(link, mediaType) }
        }
    }

    private suspend fun retrieveMedia(link: String, mediaType: MediaType) {
        val instantMedia = mediaResolver.resolveWithoutNetwork(link, mediaType)
        if (instantMedia != null) {
            setMedia(instantMedia)
            return
        }

        _media.value = Resource.Loading()
        try {
            setMedia(mediaResolver.resolve(link, mediaType))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (unsupported: MediaResolver.UnsupportedMediaException) {
            _media.value = Resource.Error()
        } catch (throwable: Throwable) {
            catchError(throwable)
        }
    }

    private fun catchError(throwable: Throwable) {
        when (throwable) {
            is IOException -> _media.value = Resource.Error(message = throwable.message)
            is HttpException -> _media.value = Resource.Error(throwable.code(), throwable.message())
            else -> _media.value = Resource.Error()
        }
    }

    fun setMedia(media: List<GalleryMedia>) {
        _media.updateValue(Resource.Success(media))
    }

    fun setMuted(mutedVideo: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setMuteVideo(mutedVideo)
        }
    }

    fun setSelectedPage(position: Int) {
        _selectedPage.updateValue(position)
    }
}
