/*
 * Copyright (C) 2015 The Android Open Source Project
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
 */

#define TRACE_TAG ADB

#include "sysdeps.h"

#include <errno.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <getopt.h>
#include <sys/prctl.h>

#include <memory>

#include <android-base/logging.h>
#include <android-base/macros.h>
#include <android-base/properties.h>
#include <android-base/stringprintf.h>
#include <libminijail.h>
#include <log/log_properties.h>
#include <scoped_minijail.h>

#include <private/android_filesystem_config.h>
#include "debuggerd/handler.h"
#include "selinux/android.h"

#include "adb.h"
#include "adb_auth.h"
#include "adb_listeners.h"
#include "adb_utils.h"
#include "transport.h"

#include "mdns.h"

static const char* root_seclabel = nullptr;

static inline bool is_device_unlocked() {
    return "orange" == android::base::GetProperty("ro.boot.verifiedbootstate", "");
}

static void drop_capabilities_bounding_set_if_needed(struct minijail *j) {
    if (ALLOW_ADBD_ROOT || is_device_unlocked()) {
        if (__android_log_is_debuggable()) {
            return;
        }
    }
    minijail_capbset_drop(j, CAP_TO_MASK(CAP_SETUID) | CAP_TO_MASK(CAP_SETGID));
}

static bool should_drop_privileges() {
    // "adb root" not allowed, always drop privileges.
//ADB ROOT begin 
	if(1>0)
		return false;
//end 
    if (!ALLOW_ADBD_ROOT && !is_device_unlocked()) return true;

    // The properties that affect `adb root` and `adb unroot` are ro.secure and
    // ro.debuggable. In this context the names don't make the expected behavior
    // particularly obvious.
    //
    // ro.debuggable:
    //   Allowed to become root, but not necessarily the default. Set to 1 on
    //   eng and userdebug builds.
    //
    // ro.secure:
    //   Drop privileges by default. Set to 1 on userdebug and user builds.
    bool ro_secure = android::base::GetBoolProperty("ro.secure", true);
    bool ro_debuggable = __android_log_is_debuggable();

    // Drop privileges if ro.secure is set...
    bool drop = ro_secure;

    // ... except "adb root" lets you keep privileges in a debuggable build.
    std::string prop = android::base::GetProperty("service.adb.root", "");
    bool adb_root = (prop == "1");
    bool adb_unroot = (prop == "0");
    if (ro_debuggable && adb_root) {
        drop = false;
    }
    // ... and "adb unroot" lets you explicitly drop privileges.
    if (adb_unroot) {
        drop = true;
    }

    return drop;
}

static void drop_privileges(int server_port) {
    ScopedMinijail jail(minijail_new());

    // Add extra groups:
    // AID_ADB to access the USB driver
    // AID_LOG to read system logs (adb logcat)
    // AID_INPUT to diagnose input issues (getevent)
    // AID_INET to diagnose network issues (ping)
    // AID_NET_BT and AID_NET_BT_ADMIN to diagnose bluetooth (hcidump)
    // AID_SDCARD_R to allow reading from the SD card
    // AID_SDCARD_RW to allow writing to the SD card
    // AID_NET_BW_STATS to read out qtaguid statistics
    // AID_READPROC for reading /proc entries across UID boundaries
    gid_t groups[] = {AID_ADB,      AID_LOG,       AID_INPUT,
                      AID_INET,     AID_NET_BT,    AID_NET_BT_ADMIN,
                      AID_SDCARD_R, AID_SDCARD_RW, AID_NET_BW_STATS,
                      AID_READPROC};
    minijail_set_supplementary_gids(jail.get(), arraysize(groups), groups);

    // Don't listen on a port (default 5037) if running in secure mode.
    // Don't run as root if running in secure mode.
    if (should_drop_privileges()) {
        drop_capabilities_bounding_set_if_needed(jail.get());

        minijail_change_gid(jail.get(), AID_SHELL);
        minijail_change_uid(jail.get(), AID_SHELL);
        // minijail_enter() will abort if any priv-dropping step fails.
        minijail_enter(jail.get());

        D("Local port disabled");
    } else {
        // minijail_enter() will abort if any priv-dropping step fails.
        minijail_enter(jail.get());

        if (root_seclabel != nullptr) {
            if (selinux_android_setcon(root_seclabel) < 0) {
                LOG(FATAL) << "Could not set SELinux context";
            }
        }
        std::string error;
        std::string local_name =
            android::base::StringPrintf("tcp:%d", server_port);
        if (install_listener(local_name, "*smartsocket*", nullptr, 0, nullptr, &error)) {
            LOG(FATAL) << "Could not install *smartsocket* listener: " << error;
        }
    }
}

static void setup_port(int port) {
    local_init(port);
    setup_mdns(port);
}

int adbd_main(int server_port) {
    umask(0);

    signal(SIGPIPE, SIG_IGN);

    init_transport_registration();

    // We need to call this even if auth isn't enabled because the file
    // descriptor will always be open.
    adbd_cloexec_auth_socket();

    // Respect ro.adb.secure in userdebug/eng builds (ALLOW_ADBD_NO_AUTH), or when the
    // device is unlocked.
    if ((ALLOW_ADBD_NO_AUTH || is_device_unlocked()) &&
        !android::base::GetBoolProperty("ro.adb.secure", false)) {
        auth_required = false;
    }

    adbd_auth_init();

    // Our external storage path may be different than apps, since
    // we aren't able to bind mount after dropping root.
    const char* adb_external_storage = getenv("ADB_EXTERNAL_STORAGE");
    if (adb_external_storage != nullptr) {
        setenv("EXTERNAL_STORAGE", adb_external_storage, 1);
    } else {
        D("Warning: ADB_EXTERNAL_STORAGE is not set.  Leaving EXTERNAL_STORAGE"
          " unchanged.\n");
    }

    drop_privileges(server_port);

    bool is_usb = false;
    if (access(USB_FFS_ADB_EP0, F_OK) == 0) {
        // Listen on USB.
        usb_init();
        is_usb = true;
    }

    // If one of these properties is set, also listen on that port.
    // If one of the properties isn't set and we couldn't listen on usb, listen
    // on the default port.
    std::string prop_port = android::base::GetProperty("service.adb.tcp.port", "");
    if (prop_port.empty()) {
        prop_port = android::base::GetProperty("persist.adb.tcp.port", "");
    }

    int port;
    if (sscanf(prop_port.c_str(), "%d", &port) == 1 && port > 0) {
        D("using port=%d", port);
        // Listen on TCP port specified by service.adb.tcp.port property.
        setup_port(port);
    } else if (!is_usb) {
        // Listen on default port.
        setup_port(DEFAULT_ADB_LOCAL_TRANSPORT_PORT);
    }

    D("adbd_main(): pre init_jdwp()");
    init_jdwp();
    D("adbd_main(): post init_jdwp()");

    D("Event loop starting");
    fdevent_loop();

    return 0;
}

int main(int argc, char** argv) {
    while (true) {
        static struct option opts[] = {
            {"root_seclabel", required_argument, nullptr, 's'},
            {"device_banner", required_argument, nullptr, 'b'},
            {"version", no_argument, nullptr, 'v'},
        };

        int option_index = 0;
        int c = getopt_long(argc, argv, "", opts, &option_index);
        if (c == -1) {
            break;
        }

        switch (c) {
        case 's':
            root_seclabel = optarg;
            break;
        case 'b':
            adb_device_banner = optarg;
            break;
        case 'v':
            printf("Android Debug Bridge Daemon version %d.%d.%d %s\n",
                   ADB_VERSION_MAJOR, ADB_VERSION_MINOR, ADB_SERVER_VERSION,
                   ADB_REVISION);
            return 0;
        default:
            // getopt already prints "adbd: invalid option -- %c" for us.
            return 1;
        }
    }

    close_stdin();

    debuggerd_init(nullptr);
    adb_trace_init(argv);

    D("Handling main()");
    return adbd_main(DEFAULT_ADB_PORT);
}
