cmake_minimum_required(VERSION 3.22.1)

PROJECT( "UsbXfer" C )

FILE ( GLOB_RECURSE SOURCES_LIB "*.c" )

SET ( CMAKE_C_FLAGS         "-Wall" )
SET ( CMAKE_C_FLAGS_DEBUG   "-g -DDEBUG")
SET ( CMAKE_C_FLAGS_RELEASE "-O1 -DNDEBUG" ) # Reduce optimization level due to suspected deadlocks on some Samsung devices

FIND_LIBRARY( LOG_LIB log )
FIND_LIBRARY( OPEN_SLES_LIB OpenSLES )

ADD_LIBRARY( ${PROJECT_NAME} SHARED ${SOURCES_LIB} )
TARGET_LINK_LIBRARIES( ${PROJECT_NAME} ${LOG_LIB} ${OPEN_SLES_LIB} )

OPTION(TBS5530_USBFS_DIAGNOSTIC "Build the debug-only usbfs diagnostic" OFF)
IF(TBS5530_USBFS_DIAGNOSTIC)
    IF(NOT CMAKE_BUILD_TYPE STREQUAL "Debug")
        MESSAGE(FATAL_ERROR "TBS5530 usbfs diagnostic is restricted to Debug builds")
    ENDIF()
    ADD_LIBRARY(Tbs5530UsbfsDiagnostic SHARED
        ../../debug/cpp/native_usbfs_core.c
        ../../debug/cpp/native_usbfs_jni.c
        ../../debug/cpp/native_fx2_control.c
        ../../debug/cpp/native_usbfs_stream.c
        ../../debug/cpp/native_usbfs_stream_jni.c)
    TARGET_COMPILE_FEATURES(Tbs5530UsbfsDiagnostic PRIVATE c_std_11)
    TARGET_COMPILE_OPTIONS(Tbs5530UsbfsDiagnostic PRIVATE -Wall -Wextra -Werror)
ENDIF()
