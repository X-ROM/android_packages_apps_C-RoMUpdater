#
# Copyright (C) 2013-2014 Dokdo Project
#
# Licensed under the GNU GPLv2 license
#
# The text of the license can be found in the LICENSE file
# or at https://www.gnu.org/licenses/gpl-2.0.txt
#

LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_PACKAGE_NAME := DokdoUpdater
LOCAL_CERTIFICATE := platform
LOCAL_SRC_FILES := $(call all-java-files-under,src)
LOCAL_MODULE_TAGS := optional
LOCAL_SDK_VERSION := 19

LOCAL_STATIC_JAVA_LIBRARIES += android-support-v4 gcm

include $(BUILD_PACKAGE)
include $(CLEAR_VARS)
LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES := gcm:libs/gcm.jar
include $(BUILD_MULTI_PREBUILT)
include $(call all-makefiles-under,$(LOCAL_PATH))
