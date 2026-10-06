package com.vitc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vitc.exception.BadRequestException;
import com.vitc.security.upload.UploadCategory;
import com.vitc.service.MediaCompatibilityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "app.media.ffmpeg-enabled=false",
        "app.media.ffprobe-binary=vitc-missing-ffprobe",
        "app.media.ffmpeg-binary=vitc-missing-ffmpeg"
})
class MediaCompatibilityWithoutToolsTest {

    @Autowired
    private MediaCompatibilityService service;

    @Test
    void compatibleMp4PassesWithoutMediaTools() {
        MockMultipartFile mp4 = new MockMultipartFile(
                "file", "Java introduction Tutorial Java Full Stack Tutorial in Hindi lecture 1 - Hum aur Code (720p, h264).mp4",
                "video/mp4", new byte[] {0, 0, 0, 24, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'});

        MediaCompatibilityService.NormalizedMedia result = service.normalize(mp4, UploadCategory.VIDEO);

        assertThat(result.file()).isSameAs(mp4);
        result.close();
    }

    @Test
    void unsupportedMediaReportsConversionRequirementWithoutTools() {
        MockMultipartFile webm = new MockMultipartFile(
                "file", "lesson.webm", "video/webm",
                new byte[] {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, 1, 2, 3, 4});

        assertThatThrownBy(() -> service.normalize(webm, UploadCategory.VIDEO))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("FFmpeg/FFprobe is required");
    }
}
