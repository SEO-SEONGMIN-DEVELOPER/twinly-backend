package com.nidus.twinly.common.aws;

import com.nidus.twinly.common.aws.bedrock.BedrockProperties;
import com.nidus.twinly.common.aws.cloudfront.CloudFrontProperties;
import com.nidus.twinly.common.aws.s3.S3Properties;
import com.nidus.twinly.common.aws.ses.SesProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.cloudfront.CloudFrontUtilities;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.ses.SesClient;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

@Configuration
public class AwsConfig {

    @Bean
    public S3Presigner s3Presigner(S3Properties s3Properties) {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                s3Properties.accessKeyId(),
                s3Properties.secretAccessKey()
        );

        return S3Presigner.builder()
                .region(Region.of(s3Properties.region()))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build();
    }

    @Bean
    public S3Client s3Client(S3Properties s3Properties) {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                s3Properties.accessKeyId(),
                s3Properties.secretAccessKey()
        );

        return S3Client.builder()
                .region(Region.of(s3Properties.region()))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build();
    }

    @Bean
    public BedrockRuntimeClient bedrockRuntimeClient(BedrockProperties bedrockProperties) {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                bedrockProperties.accessKeyId(),
                bedrockProperties.secretAccessKey()
        );

        return BedrockRuntimeClient.builder()
                .region(Region.of(bedrockProperties.region()))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build();
    }

    @Bean
    public CloudFrontUtilities cloudFrontUtilities() {
        return CloudFrontUtilities.create();
    }

    @Bean
    public PrivateKey cloudFrontPrivateKey(CloudFrontProperties cloudFrontProperties) throws GeneralSecurityException {
        byte[] der = Base64.getDecoder().decode(cloudFrontProperties.privateKeyBase64());
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    @Bean
    public SesClient sesClient(SesProperties sesProperties) {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                sesProperties.accessKeyId(),
                sesProperties.secretAccessKey()
        );

        return SesClient.builder()
                .region(Region.of(sesProperties.region()))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build();
    }
}
