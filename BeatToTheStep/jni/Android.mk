LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE	:= beat-step
LOCAL_SRC_FILES := beat-step.cpp

LOCAL_LDLIBS	+= libOpenSLES

include $(BUILD_SHARED_LIBRARY)