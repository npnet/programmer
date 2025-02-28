/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein is
 * confidential and proprietary to MediaTek Inc. and/or its licensors. Without
 * the prior written permission of MediaTek inc. and/or its licensors, any
 * reproduction, modification, use or disclosure of MediaTek Software, and
 * information contained herein, in whole or in part, shall be strictly
 * prohibited.
 *
 * MediaTek Inc. (C) 2010. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER
 * ON AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL
 * WARRANTIES, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR
 * NONINFRINGEMENT. NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH
 * RESPECT TO THE SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY,
 * INCORPORATED IN, OR SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES
 * TO LOOK ONLY TO SUCH THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO.
 * RECEIVER EXPRESSLY ACKNOWLEDGES THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO
 * OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES CONTAINED IN MEDIATEK
 * SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK SOFTWARE
 * RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S
 * ENTIRE AND CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE
 * RELEASED HEREUNDER WILL BE, AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE
 * MEDIATEK SOFTWARE AT ISSUE, OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE
 * CHARGE PAID BY RECEIVER TO MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek
 * Software") have been modified by MediaTek Inc. All revisions are subject to
 * any receiver's applicable license agreements with MediaTek Inc.
 */

#define LOG_TAG "MtkCam/CamAdapter"
//
#include <inc/CamUtils.h>
using namespace android;
using namespace MtkCamUtils;
//
#include <inc/ImgBufProvidersManager.h>
//
#include <mtkcam/hal/sensor_hal.h>
//
#include <mtkcam/hal/aaa_hal_base.h>
//
#include <mtkcam/v1/IParamsManager.h>
//
#include <mtkcam/v1/ICamAdapter.h>
#include <inc/BaseCamAdapter.h>
#include "inc/MtkDefaultCamAdapter.h"
#include <camera_custom_eis.h>
#include <cutils/properties.h>
using namespace NSMtkDefaultCamAdapter;
//


/******************************************************************************
*
*******************************************************************************/
#define MY_LOGV(fmt, arg...)        CAM_LOGV("(%d)(%s)[%s] " fmt, ::gettid(), getName(), __FUNCTION__, ##arg)
#define MY_LOGD(fmt, arg...)        CAM_LOGD("(%d)(%s)[%s] " fmt, ::gettid(), getName(), __FUNCTION__, ##arg)
#define MY_LOGI(fmt, arg...)        CAM_LOGI("(%d)(%s)[%s] " fmt, ::gettid(), getName(), __FUNCTION__, ##arg)
#define MY_LOGW(fmt, arg...)        CAM_LOGW("(%d)(%s)[%s] " fmt, ::gettid(), getName(), __FUNCTION__, ##arg)
#define MY_LOGE(fmt, arg...)        CAM_LOGE("(%d)(%s)[%s] " fmt, ::gettid(), getName(), __FUNCTION__, ##arg)
#define MY_LOGA(fmt, arg...)        CAM_LOGA("(%d)(%s)[%s] " fmt, ::gettid(), getName(), __FUNCTION__, ##arg)
#define MY_LOGF(fmt, arg...)        CAM_LOGF("(%d)(%s)[%s] " fmt, ::gettid(), getName(), __FUNCTION__, ##arg)
//
#define MY_LOGV_IF(cond, ...)       do { if ( (cond) ) { MY_LOGV(__VA_ARGS__); } }while(0)
#define MY_LOGD_IF(cond, ...)       do { if ( (cond) ) { MY_LOGD(__VA_ARGS__); } }while(0)
#define MY_LOGI_IF(cond, ...)       do { if ( (cond) ) { MY_LOGI(__VA_ARGS__); } }while(0)
#define MY_LOGW_IF(cond, ...)       do { if ( (cond) ) { MY_LOGW(__VA_ARGS__); } }while(0)
#define MY_LOGE_IF(cond, ...)       do { if ( (cond) ) { MY_LOGE(__VA_ARGS__); } }while(0)
#define MY_LOGA_IF(cond, ...)       do { if ( (cond) ) { MY_LOGA(__VA_ARGS__); } }while(0)
#define MY_LOGF_IF(cond, ...)       do { if ( (cond) ) { MY_LOGF(__VA_ARGS__); } }while(0)


/******************************************************************************
*
*******************************************************************************/
status_t
CamAdapter::
setParameters()
{
    MY_LOGD("+");
    status_t ret = OK;
    //
    //(1) must set to mpPreviewCmdQueThread earlier than to 3A
    if ( mpPreviewCmdQueThread != 0 )
    {
        if ( ! mpPreviewCmdQueThread->setParameters() )
        {
            MY_LOGE("mpPreviewCmdQueThread->setParameters fail");
        }
    }
    else
    {
        MY_LOGE("mpPreviewCmdQueThread is NULL");
    }

    // (2) get Param from 3A
    NS3A::Param_T cam3aParam;
    Hal3ABase* p3AHal = Hal3ABase::createInstance(DevMetaInfo::queryHalSensorDev(getOpenId()));
    if ( ! p3AHal )
    {
        MY_LOGE("p3AHal == NULL");
        return INVALID_OPERATION;
    }
    //
    if ( ! p3AHal->getParams(cam3aParam) )
    {
        MY_LOGE("getParams fail");
        ret = INVALID_OPERATION;
        goto lbExit;
    }

    //(3) set Param to 3A
#define UPDATE_PARAMS(param, eMapXXX, key) \
    do { \
        String8 const s = mpParamsMgr->getStr(key); \
        if  ( ! s.isEmpty() ) { \
            param = PARAMSMANAGER_MAP_INST(eMapXXX)->valueFor(s); \
        } \
    } while (0)
    //
    {
        String8 const p = mpParamsMgr->getStr(CameraParameters::KEY_PREVIEW_FPS_RANGE);
        MY_LOGI("KEY_PREVIEW_FPS_RANGE[%s]", p.string());
        char* q;
        cam3aParam.i4MinFps = strtol(p.string(),&q,10);
        cam3aParam.i4MaxFps = strtol(q+1,&q,10);
    }
    //
    if( mDefaultPreviewFpsRange[0] != cam3aParam.i4MinFps ||
        mDefaultPreviewFpsRange[1] != cam3aParam.i4MaxFps)
    {
        MY_LOGD("Use preview-fps-range");
    }
    else
    if( mDefaultPreviewFps != mpParamsMgr->getInt(CameraParameters::KEY_PREVIEW_FRAME_RATE) ||
        mpParamsMgr->getRecordingHint())
    {
       if(mpParamsMgr->getRecordingHint())
        {
            cam3aParam.i4MinFps = mpParamsMgr->getInt(CameraParameters::KEY_PREVIEW_FRAME_RATE)*1000;
            cam3aParam.i4MaxFps = mpParamsMgr->getInt(CameraParameters::KEY_PREVIEW_FRAME_RATE)*1000;
        }
        else
        {
            cam3aParam.i4MinFps   = 5000;
            cam3aParam.i4MaxFps   = 60000;
        }
    }
    else
    {
        MY_LOGD("Use default FPS");
    }
    //
    {
        MUINT32 forceFPS = ::property_get_int32("debug.forceFPS.enable", 0);
        if(forceFPS > 0)
        {
            MY_LOGD("forceFPS(%d)", forceFPS);
            cam3aParam.i4MinFps = forceFPS*1000;
            cam3aParam.i4MaxFps = forceFPS*1000;
        }
    }
    //
    MY_LOGD("FPS:RH(%d),Min(%d),Max(%d)",mpParamsMgr->getRecordingHint(),cam3aParam.i4MinFps,cam3aParam.i4MaxFps);
    //
    UPDATE_PARAMS(cam3aParam.u4AfMode, eMapFocusMode, CameraParameters::KEY_FOCUS_MODE);
    UPDATE_PARAMS(cam3aParam.u4AwbMode, eMapWhiteBalance, CameraParameters::KEY_WHITE_BALANCE);
    UPDATE_PARAMS(cam3aParam.u4SceneMode, eMapScene, CameraParameters::KEY_SCENE_MODE);
    UPDATE_PARAMS(cam3aParam.u4StrobeMode, eMapFlashMode, CameraParameters::KEY_FLASH_MODE);
    UPDATE_PARAMS(cam3aParam.u4EffectMode, eMapEffect, CameraParameters::KEY_EFFECT);
    UPDATE_PARAMS(cam3aParam.u4AntiBandingMode, eMapAntiBanding, CameraParameters::KEY_ANTIBANDING);
    //
    cam3aParam.i4ExpIndex = mpParamsMgr->getInt(CameraParameters::KEY_EXPOSURE_COMPENSATION);
    cam3aParam.fExpCompStep = mpParamsMgr->getFloat(CameraParameters::KEY_EXPOSURE_COMPENSATION_STEP);
    //
    // AE lock
    {
        String8 const s = mpParamsMgr->getStr(CameraParameters::KEY_AUTO_EXPOSURE_LOCK);
        cam3aParam.bIsAELock = ( ! s.isEmpty() && s == CameraParameters::TRUE ) ? 1 : 0;
    }
    // AWB lock
    {
        String8 const s = mpParamsMgr->getStr(CameraParameters::KEY_AUTO_WHITEBALANCE_LOCK);
        cam3aParam.bIsAWBLock = ( ! s.isEmpty() && s == CameraParameters::TRUE ) ? 1 : 0;
    }
    // AF Focus Areas
    {
        String8 const s8Area = mpParamsMgr->getStr(CameraParameters::KEY_FOCUS_AREAS);
        if  ( ! s8Area.isEmpty() )
        {
            MY_LOGD("Focus Areas:%s", s8Area.string());
            const int maxNumFocusAreas = mpParamsMgr->getInt(CameraParameters::KEY_MAX_NUM_FOCUS_AREAS);
            List <camera_area_t> focusAreas;
            mpParamsMgr->parseCamAreas(s8Area.string(), focusAreas, maxNumFocusAreas);
            int index = 0;
            for (List<camera_area_t>::iterator it = focusAreas.begin(); it != focusAreas.end(); it++) {
                cam3aParam.rFocusAreas.rAreas[index].i4Left = it->left;
                cam3aParam.rFocusAreas.rAreas[index].i4Top = it->top;
                cam3aParam.rFocusAreas.rAreas[index].i4Right = it->right;
                cam3aParam.rFocusAreas.rAreas[index].i4Bottom = it->bottom;
                cam3aParam.rFocusAreas.rAreas[index].i4Weight = it->weight;
                index++;
            }
            cam3aParam.rFocusAreas.u4Count = focusAreas.size();
        }
    }
    // AE Metering Areas
    {
        String8 const s8Area = mpParamsMgr->getStr(CameraParameters::KEY_METERING_AREAS);
        if  ( ! s8Area.isEmpty() )
        {
            MY_LOGD("Metering Areas:%s", s8Area.string());
            const int maxNumMeteringAreas = mpParamsMgr->getInt(CameraParameters::KEY_MAX_NUM_METERING_AREAS);
            List <camera_area_t> meterAreas;
            mpParamsMgr->parseCamAreas(s8Area.string(), meterAreas, maxNumMeteringAreas);
            int index = 0;
            for (List<camera_area_t>::iterator it = meterAreas.begin(); it != meterAreas.end(); it++) {
                cam3aParam.rMeteringAreas.rAreas[index].i4Left = it->left;
                cam3aParam.rMeteringAreas.rAreas[index].i4Top = it->top;
                cam3aParam.rMeteringAreas.rAreas[index].i4Right = it->right;
                cam3aParam.rMeteringAreas.rAreas[index].i4Bottom = it->bottom;
                cam3aParam.rMeteringAreas.rAreas[index].i4Weight = it->weight;
                index++;
            }
            cam3aParam.rMeteringAreas.u4Count = meterAreas.size();
        }
    }

    // MTK DEFINITION CATEGORY
    UPDATE_PARAMS(cam3aParam.u4AeMode, eMapExpMode, MtkCameraParameters::KEY_SCENE_MODE);
    UPDATE_PARAMS(cam3aParam.u4IsoSpeedMode, eMapIso, MtkCameraParameters::KEY_ISO_SPEED);
    UPDATE_PARAMS(cam3aParam.u4AfLampMode, eMapFocusLamp, MtkCameraParameters::KEY_AF_LAMP_MODE);
    //
    UPDATE_PARAMS(cam3aParam.u4BrightnessMode, eMapLevel, MtkCameraParameters::KEY_BRIGHTNESS);
    UPDATE_PARAMS(cam3aParam.u4SaturationMode, eMapLevel, MtkCameraParameters::KEY_SATURATION);
    UPDATE_PARAMS(cam3aParam.u4ContrastMode, eMapLevel, MtkCameraParameters::KEY_CONTRAST);
    UPDATE_PARAMS(cam3aParam.u4EdgeMode, eMapLevel, MtkCameraParameters::KEY_EDGE);
    UPDATE_PARAMS(cam3aParam.u4HueMode, eMapLevel, MtkCameraParameters::KEY_HUE);
    //
    cam3aParam.u4ShotMode   = mpParamsMgr->getShotMode();
    cam3aParam.u4CamMode    = mpParamsMgr->getHalAppMode();
    cam3aParam.i4RotateDegree = mpParamsMgr->getInt(MtkCameraParameters::KEY_ROTATION);
   //chenyl modify start for bug: weixin preview is dark
	//cam3aParam.bVideoStabilization = mpParamsMgr->getVideoStabilization() && GetEisLinkWithDfr();
    cam3aParam.bVideoStabilization = 0;//mpParamsMgr->getVideoStabilization() && GetEisLinkWithDfr();
   //chenyl modify end
    //
    if ( ! p3AHal->setParams(cam3aParam) )
    {
        MY_LOGE("setParams fail");
        ret = INVALID_OPERATION;
        goto lbExit;
    }

lbExit:
    p3AHal->destroyInstance();
    MY_LOGD("-");

    return ret;
}

