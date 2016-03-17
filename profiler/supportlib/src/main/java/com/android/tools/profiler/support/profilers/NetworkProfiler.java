/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.profiler.support.profilers;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import com.android.tools.profiler.support.profilerserver.MessageHeader;

import android.net.TrafficStats;
import android.os.Process;
import com.android.tools.profiler.support.profilerserver.ProfilerServer;

import java.nio.ByteBuffer;

public class NetworkProfiler extends AbstractProfilerComponent {

    private final int myUid = Process.myUid();
    private ConnectivityManager mConnectivityManager;

    @Override
    public byte getComponentId() {
        return ProfilerRegistry.NETWORKING;
    }

    @Override
    public void initialize() {
        mConnectivityManager = (ConnectivityManager) ProfilerServer.getInstance().getContext()
            .getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    @Override
    public void onClientConnection() {

    }

    @Override
    public void onClientDisconnection() {

    }

    @Override
    public String configure(byte flags) {
        return null;
    }

    @Override
    public int receiveMessage(long frameStartTime, MessageHeader header, ByteBuffer input,
            ByteBuffer output) {
        return RESPONSE_OK;
    }

    @Override
    public int update(long frameStartTime, ByteBuffer output) {
        // TODO: Change time to a customized world clock time when the world clock is ready.
        long time = System.currentTimeMillis();
        long txBytes = TrafficStats.getUidTxBytes(myUid);
        long rxBytes = TrafficStats.getUidRxBytes(myUid);
        MessageHeader.writeToBuffer(output, 39, (short) 0, (short) 0, (byte) 0x7f, ProfilerRegistry.NETWORKING, (short) 0);
        output.putLong(time);
        output.putLong(txBytes);
        output.putLong(rxBytes);

        NetworkInfo networkInfo = mConnectivityManager.getActiveNetworkInfo();
        short networkType = (short) (networkInfo != null ? networkInfo.getType() : -1);
        byte highPowerState = (byte) (mConnectivityManager.isDefaultNetworkActive() ? 0 : 1);
        output.putShort(networkType);
        output.put(highPowerState);

        return UPDATE_DONE;
    }
}