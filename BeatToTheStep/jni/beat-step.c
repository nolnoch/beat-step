/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

/* This is our own file to be used in the Beat to the Step Android application.
 *
 * Notes:
 *      SL_RATEPROP_PITCHCORAUDIO passed to setPropertyConstraints(fd, flag)
 *      should allow us to simply set the playback rate to the desired speed
 *      and not worry about pitch correction manually.
 */

#include <assert.h>
#include <jni.h>
#include <string.h>

// for __android_log_print(ANDROID_LOG_INFO, "YourApp", "formatted message");
#include <android/log.h>

// for native audio
#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>

// for native asset manager
#include <sys/types.h>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>

#ifdef ANDROID
#define LOG_TAG "JNI_DEBUG"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#else
#define LOGD printf
#define LOGE printf
#endif


// engine interfaces
static SLObjectItf engineObject = NULL;
static SLEngineItf engineEngine;

// output mix interfaces
static SLObjectItf outputMixObject = NULL;
static SLPlaybackRateItf outputMixPlaybackRate = NULL;

// decoder player interfaces
static SLObjectItf dcPlayerObject = NULL;
static SLPlayItf dcPlayerPlay;
static SLAndroidSimpleBufferQueueItf dcPlayerBufferQueue;
static SLEffectSendItf dcPlayerEffectSend;
static SLMuteSoloItf dcPlayerMuteSolo;
static SLVolumeItf dcPlayerVolume;
static SLPlaybackRateItf dcPlayerRate;

// buffer queue player interfaces
static SLObjectItf bqPlayerObject = NULL;
static SLPlayItf bqPlayerPlay;
static SLAndroidSimpleBufferQueueItf bqPlayerBufferQueue;
static SLEffectSendItf bqPlayerEffectSend;
static SLMuteSoloItf bqPlayerMuteSolo;
static SLSeekItf bqPlayerSeek;
static SLPlaybackRateItf bqPlayerRate;

// aux effect on the output mix, used by the buffer queue player
static const SLEnvironmentalReverbSettings reverbSettings =
    SL_I3DL2_ENVIRONMENT_PRESET_STONECORRIDOR;

// URI player interfaces
static SLObjectItf uriPlayerObject = NULL;
static SLPlayItf uriPlayerPlay;
static SLSeekItf uriPlayerSeek;
static SLMuteSoloItf uriPlayerMuteSolo;
static SLVolumeItf uriPlayerVolume;
static SLPlaybackRateItf uriPlayerRate;

// file descriptor player interfaces
static SLObjectItf fdPlayerObject = NULL;
static SLPlayItf fdPlayerPlay;
static SLSeekItf fdPlayerSeek;
static SLMuteSoloItf fdPlayerMuteSolo;
static SLVolumeItf fdPlayerVolume;
static SLPlaybackRateItf fdPlayerRate;

// pointer and size of the next player buffer to enqueue, and number of remaining buffers
static short *nextBuffer;
static unsigned nextSize;
static int nextCount;

#define MEGABYTE (1024 * 1024)
#define BUFFER_FRAMES (11 * MEGABYTE)
#define BUFFER_BYTES (2 * BUFFER_FRAMES)
static SLint16 songBuffer[BUFFER_FRAMES];


// this callback handler is called every time a buffer finishes playing
void bqPlayerCallback(SLAndroidSimpleBufferQueueItf bq, void *context)
{
  SLresult result;

  LOGE("Playback complete.\n");

  assert(bq == bqPlayerBufferQueue);
  assert(NULL == context);
  //result = (*bqPlayerBufferQueue)->Enqueue(bqPlayerBufferQueue, &songBuffer, BUFFER_BYTES);
  //assert(SL_RESULT_SUCCESS == result);
  //(void)result;
}

// this callback handler is called every time a buffer finishes playing
void dcPlayerCallback(SLAndroidSimpleBufferQueueItf bq, void *context)
{
  SLresult result;

  LOGE("Decoding complete.\n");

  assert(bq == dcPlayerBufferQueue);
  assert(NULL == context);
  result = (*bqPlayerBufferQueue)->Enqueue(bqPlayerBufferQueue, &songBuffer, BUFFER_BYTES);
  assert(SL_RESULT_SUCCESS == result);
  (void)result;
}


// create the engine and output mix objects
void Java_com_nolnoch_beattothestep_PlayerActivity_createEngine(JNIEnv* env, jclass clazz)
{
  SLresult result;

  assert(env != NULL);

  // create engine
  result = slCreateEngine(&engineObject, 0, NULL, 0, NULL, NULL);
  assert(SL_RESULT_SUCCESS == result);
  (void)result;

  // realize the engine
  result = (*engineObject)->Realize(engineObject, SL_BOOLEAN_FALSE);
  assert(SL_RESULT_SUCCESS == result);
  (void)result;

  // get the engine interface, which is needed in order to create other objects
  result = (*engineObject)->GetInterface(engineObject, SL_IID_ENGINE, &engineEngine);
  assert(SL_RESULT_SUCCESS == result);
  (void)result;

  // create output mix, with environmental reverb specified as a non-required interface
  result = (*engineEngine)->CreateOutputMix(engineEngine, &outputMixObject, 0, NULL, NULL);
  assert(SL_RESULT_SUCCESS == result);
  (void)result;

  // realize the output mix
  result = (*outputMixObject)->Realize(outputMixObject, SL_BOOLEAN_FALSE);
  assert(SL_RESULT_SUCCESS == result);
  (void)result;

}


// create buffer queue audio player
void Java_com_nolnoch_beattothestep_PlayerActivity_createBufferQueueAudioPlayer(JNIEnv* env,
    jclass clazz)
{
  SLresult result;

  assert(env != NULL);

  // configure audio source
  SLDataLocator_AndroidSimpleBufferQueue loc_bufq = {SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE, 1};
  SLDataFormat_PCM format_pcm = {
      SL_DATAFORMAT_PCM,
      2,
      SL_SAMPLINGRATE_44_1,
      SL_PCMSAMPLEFORMAT_FIXED_16,
      SL_PCMSAMPLEFORMAT_FIXED_16,
      SL_SPEAKER_FRONT_LEFT | SL_SPEAKER_FRONT_RIGHT,
      SL_BYTEORDER_LITTLEENDIAN
  };
  SLDataSource audioSrc = {&loc_bufq, &format_pcm};

  // configure audio sink
  SLDataLocator_OutputMix loc_outmix = {SL_DATALOCATOR_OUTPUTMIX, outputMixObject};
  SLDataSink audioSnk = {&loc_outmix, NULL};

  // create audio player
  const SLInterfaceID ids[4] = {SL_IID_BUFFERQUEUE, SL_IID_EFFECTSEND, SL_IID_SEEK, SL_IID_PLAYBACKRATE};
  const SLboolean req[4] = {SL_BOOLEAN_TRUE, SL_BOOLEAN_TRUE, SL_BOOLEAN_FALSE, SL_BOOLEAN_TRUE};
  result = (*engineEngine)->CreateAudioPlayer(engineEngine, &bqPlayerObject, &audioSrc, &audioSnk,
      4, ids, req);
  assert(SL_RESULT_SUCCESS == result);
  (void)result;

  // realize the player
  result = (*bqPlayerObject)->Realize(bqPlayerObject, SL_BOOLEAN_FALSE);
  assert(SL_RESULT_SUCCESS == result);
  (void)result;

  // get the play interface
  result = (*bqPlayerObject)->GetInterface(bqPlayerObject, SL_IID_PLAY, &bqPlayerPlay);
  assert(SL_RESULT_SUCCESS == result);
  (void)result;

  // get the buffer queue interface
  result = (*bqPlayerObject)->GetInterface(bqPlayerObject, SL_IID_BUFFERQUEUE,
      &bqPlayerBufferQueue);
  assert(SL_RESULT_SUCCESS == result);
  (void)result;

  // register callback on the buffer queue
  result = (*bqPlayerBufferQueue)->RegisterCallback(bqPlayerBufferQueue, bqPlayerCallback, NULL);
  assert(SL_RESULT_SUCCESS == result);
  (void)result;

  // get the effect send interface
  result = (*bqPlayerObject)->GetInterface(bqPlayerObject, SL_IID_EFFECTSEND,
      &bqPlayerEffectSend);
  assert(SL_RESULT_SUCCESS == result);
  (void)result;

#if 0   // mute/solo is not supported for sources that are known to be mono, as this is
  // get the mute/solo interface
  result = (*bqPlayerObject)->GetInterface(bqPlayerObject, SL_IID_MUTESOLO, &bqPlayerMuteSolo);
  assert(SL_RESULT_SUCCESS == result);
  (void)result;
#endif

  // get the volume interface
  result = (*bqPlayerObject)->GetInterface(bqPlayerObject, SL_IID_SEEK, &bqPlayerSeek);
  assert(SL_RESULT_SUCCESS == result);
  (void)result;

  // get the volume interface
  result = (*bqPlayerObject)->GetInterface(bqPlayerObject, SL_IID_PLAYBACKRATE, &bqPlayerRate);
  assert(SL_RESULT_SUCCESS == result);
  (void)result;

  // set the player's state to playing
  //result = (*bqPlayerPlay)->SetPlayState(bqPlayerPlay, SL_PLAYSTATE_PLAYING);
  //assert(SL_RESULT_SUCCESS == result);
  //(void)result;
}


// set the playing state for the BQ audio player
// to PLAYING (true) or PAUSED (false)
void Java_com_nolnoch_beattothestep_PlayerActivity_setPlayingBufferedQueueAudioPlayer(JNIEnv* env,
    jclass clazz, jboolean isPlaying)
{
  SLresult result;

  // make sure the BQ audio player was created
  if (NULL != bqPlayerPlay) {

    // set the player's state
    result = (*bqPlayerPlay)->SetPlayState(bqPlayerPlay, isPlaying ?
        SL_PLAYSTATE_PLAYING : SL_PLAYSTATE_PAUSED);
    assert(SL_RESULT_SUCCESS == result);
    (void)result;
  }

}


void Java_com_nolnoch_beattothestep_PlayerActivity_setResetBufferedQueueAudioPlayer(JNIEnv* env,
    jclass clazz)
{
  SLresult result;
  SLmillisecond pos = 0;

  // make sure the BQ audio player was created
  if (NULL != bqPlayerSeek) {

    // set the player's state
    result = (*bqPlayerSeek)->SetPosition(bqPlayerSeek, pos, SL_SEEKMODE_FAST);
    assert(SL_RESULT_SUCCESS == result);
    (void)result;
  }

}

void Java_com_nolnoch_beattothestep_PlayerActivity_setSeekBufferedQueueAudioPlayer(JNIEnv* env,
    jclass clazz)
{
  SLresult result;
  SLmillisecond pos = 0;

  // make sure the BQ audio player was created
  if ((NULL != bqPlayerSeek) && (NULL != bqPlayerPlay)) {

    result = (*bqPlayerPlay)->GetPosition(bqPlayerPlay, &pos);
    assert(SL_RESULT_SUCCESS == result);
    (void)result;

    pos += 10 * 1000;

    // set the player's state
    result = (*bqPlayerSeek)->SetPosition(bqPlayerSeek, pos, SL_SEEKMODE_FAST);
    assert(SL_RESULT_SUCCESS == result);
    (void)result;
  }

}


// create buffer queue decoding player
jboolean Java_com_nolnoch_beattothestep_PlayerActivity_createDecoderPlayer(JNIEnv* env,
    jclass clazz, jstring uri)
{
  SLresult result;

  // convert Java string to UTF-8
  const char *utf8 = (*env)->GetStringUTFChars(env, uri, NULL);
  assert(NULL != utf8);

  assert(env != NULL);

  // configure audio source
  // (requires the INTERNET permission depending on the uri parameter)
  SLDataLocator_URI loc_uri = {SL_DATALOCATOR_URI, (SLchar *) utf8};
  SLDataFormat_MIME format_mime = {SL_DATAFORMAT_MIME, NULL, SL_CONTAINERTYPE_UNSPECIFIED};
  SLDataSource audioSrc = {&loc_uri, &format_mime};

  // configure audio sink
  SLDataLocator_AndroidSimpleBufferQueue loc_bufq = {SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE, 1};
  SLDataFormat_PCM format_pcm = {
      SL_DATAFORMAT_PCM,
      2,
      SL_SAMPLINGRATE_44_1,
      SL_PCMSAMPLEFORMAT_FIXED_16,
      SL_PCMSAMPLEFORMAT_FIXED_16,
      SL_SPEAKER_FRONT_LEFT | SL_SPEAKER_FRONT_RIGHT,
      SL_BYTEORDER_LITTLEENDIAN
  };
  SLDataSink audioSnk = {&loc_bufq, &format_pcm};

  // create audio player
  const SLInterfaceID ids[3] = {SL_IID_BUFFERQUEUE, SL_IID_EFFECTSEND,
      /*SL_IID_MUTESOLO,*/ SL_IID_VOLUME};
  const SLboolean req[3] = {SL_BOOLEAN_TRUE, SL_BOOLEAN_TRUE,
      /*SL_BOOLEAN_TRUE,*/ SL_BOOLEAN_TRUE};
  result = (*engineEngine)->CreateAudioPlayer(engineEngine, &dcPlayerObject, &audioSrc, &audioSnk,
      3, ids, req);
  assert(SL_RESULT_SUCCESS == result);
  (void)result;

  // realize the player
  result = (*dcPlayerObject)->Realize(dcPlayerObject, SL_BOOLEAN_FALSE);
  if (SL_RESULT_SUCCESS != result) {
    (*dcPlayerObject)->Destroy(dcPlayerObject);
    dcPlayerObject = NULL;
    return JNI_FALSE;
  }

  // get the play interface
  result = (*dcPlayerObject)->GetInterface(dcPlayerObject, SL_IID_PLAY, &dcPlayerPlay);
  assert(SL_RESULT_SUCCESS == result);
  (void)result;

  // get the buffer queue interface
  result = (*dcPlayerObject)->GetInterface(dcPlayerObject, SL_IID_BUFFERQUEUE,
      &dcPlayerBufferQueue);
  assert(SL_RESULT_SUCCESS == result);
  (void)result;

  // register callback on the buffer queue
  result = (*dcPlayerBufferQueue)->RegisterCallback(dcPlayerBufferQueue, dcPlayerCallback, NULL);
  assert(SL_RESULT_SUCCESS == result);
  (void)result;

  // get the effect send interface
  result = (*dcPlayerObject)->GetInterface(dcPlayerObject, SL_IID_EFFECTSEND,
      &dcPlayerEffectSend);
  assert(SL_RESULT_SUCCESS == result);
  (void)result;

  // get the volume interface
  result = (*dcPlayerObject)->GetInterface(dcPlayerObject, SL_IID_VOLUME, &dcPlayerVolume);
  assert(SL_RESULT_SUCCESS == result);
  (void)result;

  return JNI_TRUE;
}


// Enqueue the decoder to being decoding
void Java_com_nolnoch_beattothestep_PlayerActivity_setDecodingDecoderPlayer(JNIEnv* env,
    jclass clazz)
{
  SLresult result;

  LOGE("Decoding.\n");

  // make sure the decoder player was created
  if (NULL != dcPlayerPlay) {

    result = (*dcPlayerBufferQueue)->Enqueue(dcPlayerBufferQueue, &songBuffer, BUFFER_BYTES);
    assert(SL_RESULT_SUCCESS == result);
    (void)result;

    // set the player's state to playing
    result = (*dcPlayerPlay)->SetPlayState(dcPlayerPlay, SL_PLAYSTATE_PLAYING);
    assert(SL_RESULT_SUCCESS == result);
    (void)result;
  }

}


// create URI audio player
jboolean Java_com_nolnoch_beattothestep_PlayerActivity_createUriAudioPlayer(JNIEnv *env, jclass clazz,
    jstring uri)
{
  SLresult result;

  // convert Java string to UTF-8
  const char *utf8 = (*env)->GetStringUTFChars(env, uri, NULL);
  assert(NULL != utf8);

  // configure audio source
  // (requires the INTERNET permission depending on the uri parameter)
  SLDataLocator_URI loc_uri = {SL_DATALOCATOR_URI, (SLchar *) utf8};
  SLDataFormat_MIME format_mime = {SL_DATAFORMAT_MIME, NULL, SL_CONTAINERTYPE_UNSPECIFIED};
  SLDataSource audioSrc = {&loc_uri, &format_mime};

  // configure audio sink
  SLDataLocator_OutputMix loc_outmix = {SL_DATALOCATOR_OUTPUTMIX, outputMixObject};
  SLDataSink audioSnk = {&loc_outmix, NULL};

  // create audio player
  const SLInterfaceID ids[4] = {SL_IID_SEEK, SL_IID_MUTESOLO, SL_IID_VOLUME, SL_IID_PLAYBACKRATE};
  const SLboolean req[4] = {SL_BOOLEAN_TRUE, SL_BOOLEAN_TRUE, SL_BOOLEAN_TRUE, SL_BOOLEAN_TRUE};
  result = (*engineEngine)->CreateAudioPlayer(engineEngine, &uriPlayerObject, &audioSrc,
      &audioSnk, 4, ids, req);
  // note that an invalid URI is not detected here, but during prepare/prefetch on Android,
  // or possibly during Realize on other platforms
  assert(SL_RESULT_SUCCESS == result);
  (void)result;

  // release the Java string and UTF-8
  (*env)->ReleaseStringUTFChars(env, uri, utf8);

  // realize the player
  result = (*uriPlayerObject)->Realize(uriPlayerObject, SL_BOOLEAN_FALSE);
  // this will always succeed on Android, but we check result for portability to other platforms
  if (SL_RESULT_SUCCESS != result) {
    (*uriPlayerObject)->Destroy(uriPlayerObject);
    uriPlayerObject = NULL;
    return JNI_FALSE;
  }

  // get the play interface
  result = (*uriPlayerObject)->GetInterface(uriPlayerObject, SL_IID_PLAY, &uriPlayerPlay);
  assert(SL_RESULT_SUCCESS == result);
  (void)result;

  // get the seek interface
  result = (*uriPlayerObject)->GetInterface(uriPlayerObject, SL_IID_SEEK, &uriPlayerSeek);
  assert(SL_RESULT_SUCCESS == result);
  (void)result;

  // get the mute/solo interface
  result = (*uriPlayerObject)->GetInterface(uriPlayerObject, SL_IID_MUTESOLO, &uriPlayerMuteSolo);
  assert(SL_RESULT_SUCCESS == result);
  (void)result;

  // get the volume interface
  result = (*uriPlayerObject)->GetInterface(uriPlayerObject, SL_IID_VOLUME, &uriPlayerVolume);
  assert(SL_RESULT_SUCCESS == result);
  (void)result;

  // get the playback rate interface
  result = (*uriPlayerObject)->GetInterface(uriPlayerObject, SL_IID_PLAYBACKRATE, &uriPlayerRate);
  assert(SL_RESULT_SUCCESS == result);
  (void)result;

  return JNI_TRUE;
}


// set the playing state for the URI audio player
// to PLAYING (true) or PAUSED (false)
void Java_com_nolnoch_beattothestep_PlayerActivity_setPlayingUriAudioPlayer(JNIEnv* env,
    jclass clazz, jboolean isPlaying)
{
  SLresult result;

  // make sure the URI audio player was created
  if (NULL != uriPlayerPlay) {

    // set the player's state
    result = (*uriPlayerPlay)->SetPlayState(uriPlayerPlay, isPlaying ?
        SL_PLAYSTATE_PLAYING : SL_PLAYSTATE_PAUSED);
    assert(SL_RESULT_SUCCESS == result);
    (void)result;
  }

}


// set the whole file looping state for the URI audio player
void Java_com_nolnoch_beattothestep_PlayerActivity_setLoopingUriAudioPlayer(JNIEnv* env,
    jclass clazz, jboolean isLooping)
{
  SLresult result;

  // make sure the URI audio player was created
  if (NULL != uriPlayerSeek) {

    // set the looping state
    result = (*uriPlayerSeek)->SetLoop(uriPlayerSeek, (SLboolean) isLooping, 0,
        SL_TIME_UNKNOWN);
    assert(SL_RESULT_SUCCESS == result);
    (void)result;
  }

}


// expose the mute/solo APIs to Java for one of the 3 players

static SLMuteSoloItf getMuteSolo()
{
  if (uriPlayerMuteSolo != NULL)
    return uriPlayerMuteSolo;
  else if (fdPlayerMuteSolo != NULL)
    return fdPlayerMuteSolo;
  else
    return bqPlayerMuteSolo;
}

void Java_com_nolnoch_beattothestep_PlayerActivity_setChannelMuteUriAudioPlayer(JNIEnv* env,
    jclass clazz, jint chan, jboolean mute)
{
  SLresult result;
  SLMuteSoloItf muteSoloItf = getMuteSolo();
  if (NULL != muteSoloItf) {
    result = (*muteSoloItf)->SetChannelMute(muteSoloItf, chan, mute);
    assert(SL_RESULT_SUCCESS == result);
    (void)result;
  }
}

void Java_com_nolnoch_beattothestep_PlayerActivity_setChannelSoloUriAudioPlayer(JNIEnv* env,
    jclass clazz, jint chan, jboolean solo)
{
  SLresult result;
  SLMuteSoloItf muteSoloItf = getMuteSolo();
  if (NULL != muteSoloItf) {
    result = (*muteSoloItf)->SetChannelSolo(muteSoloItf, chan, solo);
    assert(SL_RESULT_SUCCESS == result);
    (void)result;
  }
}

int Java_com_nolnoch_beattothestep_PlayerActivity_getNumChannelsUriAudioPlayer(JNIEnv* env, jclass clazz)
{
  SLuint8 numChannels;
  SLresult result;
  SLMuteSoloItf muteSoloItf = getMuteSolo();
  if (NULL != muteSoloItf) {
    result = (*muteSoloItf)->GetNumChannels(muteSoloItf, &numChannels);
    if (SL_RESULT_PRECONDITIONS_VIOLATED == result) {
      // channel count is not yet known
      numChannels = 0;
    } else {
      assert(SL_RESULT_SUCCESS == result);
    }
  } else {
    numChannels = 0;
  }
  return numChannels;
}

// expose the volume APIs to Java for one of the 3 players

static SLVolumeItf getVolume()
{
  if (uriPlayerVolume != NULL)
    return uriPlayerVolume;
  else
    return fdPlayerVolume;
}

void Java_com_nolnoch_beattothestep_PlayerActivity_setVolumeUriAudioPlayer(JNIEnv* env, jclass clazz,
    jint millibel)
{
  SLresult result;
  SLVolumeItf volumeItf = getVolume();
  if (NULL != volumeItf) {
    result = (*volumeItf)->SetVolumeLevel(volumeItf, millibel);
    assert(SL_RESULT_SUCCESS == result);
    (void)result;
  }
}

void Java_com_nolnoch_beattothestep_PlayerActivity_setMuteUriAudioPlayer(JNIEnv* env, jclass clazz,
    jboolean mute)
{
  SLresult result;
  SLVolumeItf volumeItf = getVolume();
  if (NULL != volumeItf) {
    result = (*volumeItf)->SetMute(volumeItf, mute);
    assert(SL_RESULT_SUCCESS == result);
    (void)result;
  }
}

void Java_com_nolnoch_beattothestep_PlayerActivity_enableStereoPositionUriAudioPlayer(JNIEnv* env,
    jclass clazz, jboolean enable)
{
  SLresult result;
  SLVolumeItf volumeItf = getVolume();
  if (NULL != volumeItf) {
    result = (*volumeItf)->EnableStereoPosition(volumeItf, enable);
    assert(SL_RESULT_SUCCESS == result);
    (void)result;
  }
}

void Java_com_nolnoch_beattothestep_PlayerActivity_setStereoPositionUriAudioPlayer(JNIEnv* env,
    jclass clazz, jint permille)
{
  SLresult result;
  SLVolumeItf volumeItf = getVolume();
  if (NULL != volumeItf) {
    result = (*volumeItf)->SetStereoPosition(volumeItf, permille);
    assert(SL_RESULT_SUCCESS == result);
    (void)result;
  }
}


// select the desired clip and play count, and enqueue the first buffer if idle
jboolean Java_com_nolnoch_beattothestep_PlayerActivity_selectClip(JNIEnv* env, jclass clazz, jint which,
    jint count)
{
  switch (which) {
    case 0:     // CLIP_NONE
      nextBuffer = (short *) NULL;
      nextSize = 0;
      break;
    default:
      nextBuffer = NULL;
      nextSize = 0;
      break;
  }
  nextCount = count;
  if (nextSize > 0) {
    // here we only enqueue one buffer because it is a long clip,
    // but for streaming playback we would typically enqueue at least 2 buffers to start
    SLresult result;
    result = (*bqPlayerBufferQueue)->Enqueue(bqPlayerBufferQueue, nextBuffer, nextSize);
    if (SL_RESULT_SUCCESS != result) {
      return JNI_FALSE;
    }
  }

  return JNI_TRUE;
}


// create asset audio player
jboolean Java_com_nolnoch_beattothestep_PlayerActivity_createAssetAudioPlayer(JNIEnv* env, jclass clazz,
    jobject assetManager, jstring filename)
{
  SLresult result;

  assert(env != NULL);

  // convert Java string to UTF-8
  const char *utf8 = (*env)->GetStringUTFChars(env, filename, NULL);
  assert(NULL != utf8);

  // use asset manager to open asset by filename
  AAssetManager* mgr = AAssetManager_fromJava(env, assetManager);
  assert(NULL != mgr);
  AAsset* asset = AAssetManager_open(mgr, utf8, AASSET_MODE_UNKNOWN);

  // release the Java string and UTF-8
  (*env)->ReleaseStringUTFChars(env, filename, utf8);

  // the asset might not be found
  if (NULL == asset) {
    return JNI_FALSE;
  }

  // open asset as file descriptor
  off_t start, length;
  int fd = AAsset_openFileDescriptor(asset, &start, &length);
  assert(0 <= fd);
  AAsset_close(asset);

  // configure audio source
  SLDataLocator_AndroidFD loc_fd = {SL_DATALOCATOR_ANDROIDFD, fd, start, length};
  SLDataFormat_MIME format_mime = {SL_DATAFORMAT_MIME, NULL, SL_CONTAINERTYPE_UNSPECIFIED};
  SLDataSource audioSrc = {&loc_fd, &format_mime};

  // configure audio sink
  SLDataLocator_OutputMix loc_outmix = {SL_DATALOCATOR_OUTPUTMIX, outputMixObject};
  SLDataSink audioSnk = {&loc_outmix, NULL};

  // create audio player
  const SLInterfaceID ids[4] = {SL_IID_SEEK, SL_IID_MUTESOLO, SL_IID_VOLUME, SL_IID_PLAYBACKRATE};
  const SLboolean req[4] = {SL_BOOLEAN_TRUE, SL_BOOLEAN_TRUE, SL_BOOLEAN_TRUE, SL_BOOLEAN_TRUE};
  result = (*engineEngine)->CreateAudioPlayer(engineEngine, &fdPlayerObject, &audioSrc, &audioSnk,
      4, ids, req);
  assert(SL_RESULT_SUCCESS == result);
  (void)result;

  // realize the player
  result = (*fdPlayerObject)->Realize(fdPlayerObject, SL_BOOLEAN_FALSE);
  assert(SL_RESULT_SUCCESS == result);
  (void)result;

  // get the play interface
  result = (*fdPlayerObject)->GetInterface(fdPlayerObject, SL_IID_PLAY, &fdPlayerPlay);
  assert(SL_RESULT_SUCCESS == result);
  (void)result;

  // get the seek interface
  result = (*fdPlayerObject)->GetInterface(fdPlayerObject, SL_IID_SEEK, &fdPlayerSeek);
  assert(SL_RESULT_SUCCESS == result);
  (void)result;

  // get the mute/solo interface
  result = (*fdPlayerObject)->GetInterface(fdPlayerObject, SL_IID_MUTESOLO, &fdPlayerMuteSolo);
  assert(SL_RESULT_SUCCESS == result);
  (void)result;

  // get the volume interface
  result = (*fdPlayerObject)->GetInterface(fdPlayerObject, SL_IID_VOLUME, &fdPlayerVolume);
  assert(SL_RESULT_SUCCESS == result);
  (void)result;

  // get the volume interface
  result = (*fdPlayerObject)->GetInterface(fdPlayerObject, SL_IID_PLAYBACKRATE, &fdPlayerRate);
  assert(SL_RESULT_SUCCESS == result);
  (void)result;

  // enable whole file looping
  result = (*fdPlayerSeek)->SetLoop(fdPlayerSeek, SL_BOOLEAN_TRUE, 0, SL_TIME_UNKNOWN);
  assert(SL_RESULT_SUCCESS == result);
  (void)result;

  return JNI_TRUE;
}

// set the playing state for the asset audio player
void Java_com_nolnoch_beattothestep_PlayerActivity_setPlayingAssetAudioPlayer(JNIEnv* env,
    jclass clazz, jboolean isPlaying)
{
  SLresult result;

  // make sure the asset audio player was created
  if (NULL != fdPlayerPlay) {

    // set the player's state
    result = (*fdPlayerPlay)->SetPlayState(fdPlayerPlay, isPlaying ?
        SL_PLAYSTATE_PLAYING : SL_PLAYSTATE_PAUSED);
    assert(SL_RESULT_SUCCESS == result);
    (void)result;
  }

}

// shut down the native audio system
void Java_com_nolnoch_beattothestep_PlayerActivity_shutdown(JNIEnv* env, jclass clazz)
{

  // destroy buffer queue audio player object, and invalidate all associated interfaces
  if (bqPlayerObject != NULL) {
    (*bqPlayerObject)->Destroy(bqPlayerObject);
    bqPlayerObject = NULL;
    bqPlayerPlay = NULL;
    bqPlayerBufferQueue = NULL;
    bqPlayerEffectSend = NULL;
    bqPlayerMuteSolo = NULL;
    bqPlayerSeek = NULL;
    bqPlayerRate = NULL;
  }

  // destroy buffer queue audio player object, and invalidate all associated interfaces
  if (dcPlayerObject != NULL) {
    (*dcPlayerObject)->Destroy(dcPlayerObject);
    dcPlayerObject = NULL;
    dcPlayerPlay = NULL;
    dcPlayerBufferQueue = NULL;
    dcPlayerEffectSend = NULL;
    dcPlayerMuteSolo = NULL;
    dcPlayerRate = NULL;
  }

  // destroy file descriptor audio player object, and invalidate all associated interfaces
  if (fdPlayerObject != NULL) {
    (*fdPlayerObject)->Destroy(fdPlayerObject);
    fdPlayerObject = NULL;
    fdPlayerPlay = NULL;
    fdPlayerSeek = NULL;
    fdPlayerMuteSolo = NULL;
    fdPlayerVolume = NULL;
    fdPlayerRate = NULL;
  }

  // destroy URI audio player object, and invalidate all associated interfaces
  if (uriPlayerObject != NULL) {
    (*uriPlayerObject)->Destroy(uriPlayerObject);
    uriPlayerObject = NULL;
    uriPlayerPlay = NULL;
    uriPlayerSeek = NULL;
    uriPlayerMuteSolo = NULL;
    uriPlayerVolume = NULL;
    uriPlayerRate = NULL;
  }

  // destroy output mix object, and invalidate all associated interfaces
  if (outputMixObject != NULL) {
    (*outputMixObject)->Destroy(outputMixObject);
    outputMixObject = NULL;
    outputMixPlaybackRate = NULL;
  }

  // destroy engine object, and invalidate all associated interfaces
  if (engineObject != NULL) {
    (*engineObject)->Destroy(engineObject);
    engineObject = NULL;
    engineEngine = NULL;
  }

}

static SLPlaybackRateItf getRate()
{
  if (uriPlayerRate != NULL)
    return uriPlayerRate;
  else if (fdPlayerRate != NULL)
    return fdPlayerRate;
  else
    return bqPlayerRate;
}

int Java_com_nolnoch_beattothestep_PlayerActivity_getPlaybackRate(JNIEnv *env, jclass clazz) {
  SLpermille rate;
  SLresult result;

  SLuint32 capabilities = 0;
  SLpermille minRate = 0;
  SLpermille maxRate = 0;
  SLpermille stepSize = 0;

  rate = 0;
  SLPlaybackRateItf playbackRateItf = bqPlayerRate;
  if (NULL != playbackRateItf) {
    result = (*playbackRateItf)->GetRateRange(playbackRateItf, 0, &minRate, &maxRate, &stepSize, &capabilities);
    assert(SL_RESULT_SUCCESS == result);
    (void)result;
  }

  LOGD("Min: %d, Max: %d, Step: %d, Options: %d\n", minRate, maxRate, stepSize, capabilities);

  return rate;
}

void Java_com_nolnoch_beattothestep_PlayerActivity_setPlaybackRate(JNIEnv *env, jclass clazz, jint rate) {
  SLresult result;
  SLpermille pRate = rate;
  SLuint32 capabilities = 0;
  SLpermille minRate = 0;
  SLpermille maxRate = 0;
  SLpermille stepSize = 0;

  SLPlaybackRateItf playbackRateItf = bqPlayerRate;
  if (NULL != playbackRateItf) {
    result = (*playbackRateItf)->GetRateRange(playbackRateItf, 0, &minRate, &maxRate, &stepSize, &capabilities);
    assert(SL_RESULT_SUCCESS == result);
    (void)result;
    
    result = (*playbackRateItf)->SetPropertyConstraints(playbackRateItf, (capabilities & SL_RATEPROP_PITCHCORAUDIO)
        ? SL_RATEPROP_PITCHCORAUDIO : SL_RATEPROP_NOPITCHCORAUDIO);
    assert(SL_RESULT_SUCCESS == result);
    (void)result;
    
    result = (*playbackRateItf)->SetRate(playbackRateItf, pRate);
    assert(SL_RESULT_SUCCESS == result);
    (void)result;
    
    LOGD("Set playback rate to: %d\n", rate);
  }
}
