/*
 * Copyright 2022 The Android Open Source Project
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

package com.google.android.exoplayer2.transformer;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.util.Util;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/* package */ final class SilentAudioGenerator {
  private static final int DEFAULT_BUFFER_SIZE_FRAMES = 1024;

  private final ByteBuffer internalBuffer;

  private long remainingBytesToOutput;

  public SilentAudioGenerator(Format format, long totalDurationUs) {
    int frameSize =
        Util.getPcmFrameSize(
            format.pcmEncoding == Format.NO_VALUE ? C.ENCODING_PCM_16BIT : format.pcmEncoding,
            format.channelCount);
    long outputFrameCount = (format.sampleRate * totalDurationUs) / C.MICROS_PER_SECOND;
    remainingBytesToOutput = frameSize * outputFrameCount;
    internalBuffer =
        ByteBuffer.allocateDirect(DEFAULT_BUFFER_SIZE_FRAMES * frameSize)
            .order(ByteOrder.nativeOrder());
    internalBuffer.flip();
  }

  public ByteBuffer getBuffer() {
    if (!internalBuffer.hasRemaining()) {
      // "next" buffer.
      internalBuffer.clear();
      if (remainingBytesToOutput < internalBuffer.capacity()) {
        internalBuffer.limit((int) remainingBytesToOutput);
      }
      // Only reduce remaining bytes when we "generate" a new one.
      remainingBytesToOutput -= internalBuffer.remaining();
    }
    return internalBuffer;
  }

  public boolean isEnded() {
    return !internalBuffer.hasRemaining() && remainingBytesToOutput == 0;
  }
}
