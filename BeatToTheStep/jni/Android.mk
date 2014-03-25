LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE	:= beat-step
LOCAL_SRC_FILES := beat-step.c

LOCAL_LDLIBS	+= -lOpenSLES
LOCAL_LDLIBS	+= -landroid

include $(BUILD_SHARED_LIBRARY)