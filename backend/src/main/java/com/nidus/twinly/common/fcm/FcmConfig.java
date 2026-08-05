package com.nidus.twinly.common.fcm;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;

@Configuration
public class FcmConfig {

    @Bean
    public FirebaseMessaging firebaseMessaging(FcmProperties fcmProperties) {
        byte[] serviceAccount = Base64.getDecoder().decode(fcmProperties.serviceAccountBase64());

        try (InputStream credentials = new ByteArrayInputStream(serviceAccount)) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(credentials))
                    .build();

            FirebaseApp app = FirebaseApp.getApps().stream()
                    .filter(existing -> FirebaseApp.DEFAULT_APP_NAME.equals(existing.getName()))
                    .findFirst()
                    .orElseGet(() -> FirebaseApp.initializeApp(options));

            return FirebaseMessaging.getInstance(app);
        } catch (IOException e) {
            throw new IllegalStateException("FCM 자격 증명 초기화에 실패했습니다.", e);
        }
    }
}
