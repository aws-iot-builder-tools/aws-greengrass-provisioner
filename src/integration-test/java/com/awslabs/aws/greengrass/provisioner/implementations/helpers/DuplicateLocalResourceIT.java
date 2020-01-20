package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.AwsGreengrassProvisioner;
import com.awslabs.aws.greengrass.provisioner.data.resources.*;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class DuplicateLocalResourceIT {
    public static final String TEMPDIR_1 = "/tempdir1";
    public static final String TMP_1 = "/tmp1";
    public static final String TMP_2 = "/tmp2";
    public static final String TMP = "/tmp";
    public static final String TEMPDIR_2 = "/tempdir2";
    private BasicGreengrassHelper basicGreengrassHelper;
    private List<LocalDeviceResource> localDeviceResources = new ArrayList<>();
    private List<LocalVolumeResource> localVolumeResources = new ArrayList<>();
    private List<LocalS3Resource> localS3Resources = new ArrayList<>();
    private List<LocalSageMakerResource> localSageMakerResources = new ArrayList<>();
    private List<LocalSecretsManagerResource> localSecretsManagerResources = new ArrayList<>();

    @Before
    public void setup() {
        basicGreengrassHelper = AwsGreengrassProvisioner.getInjector().getInstance(BasicGreengrassHelper.class);
    }

    @Test
    public void shouldFilterOutDuplicateLocalVolumeResources() {
        LocalVolumeResource localVolumeResource1 = ImmutableLocalVolumeResource.builder()
                .sourcePath(TMP)
                .destinationPath(TMP)
                .isReadWrite(true)
                .build();

        LocalVolumeResource localVolumeResource2 = ImmutableLocalVolumeResource.copyOf(localVolumeResource1);

        localVolumeResources.clear();
        localVolumeResources.add(localVolumeResource1);
        localVolumeResources.add(localVolumeResource2);

        basicGreengrassHelper.createResourceDefinition(localDeviceResources, localVolumeResources, localS3Resources, localSageMakerResources, localSecretsManagerResources);
    }

    @Test
    public void shouldFailWhenSourcePathsAreTheSameAndDestinationPathsAreDifferent() {
        LocalVolumeResource localVolumeResource1 = ImmutableLocalVolumeResource.builder()
                .sourcePath(TMP)
                .destinationPath(TEMPDIR_1)
                .isReadWrite(true)
                .build();

        LocalVolumeResource localVolumeResource2 = ImmutableLocalVolumeResource.builder()
                .sourcePath(TMP)
                .destinationPath(TEMPDIR_2)
                .isReadWrite(true)
                .build();

        localVolumeResources.clear();
        localVolumeResources.add(localVolumeResource1);
        localVolumeResources.add(localVolumeResource2);

        RuntimeException runtimeException = Assert.assertThrows(RuntimeException.class, () -> basicGreengrassHelper.createResourceDefinition(localDeviceResources, localVolumeResources, localS3Resources, localSageMakerResources, localSecretsManagerResources));
        Assert.assertTrue(runtimeException.getMessage().contains("Invalid resource configuration"));
    }

    @Test
    public void shouldNotFailWhenSourcePathsAreDifferentAndDestinationPathsAreTheSame() {
        LocalVolumeResource localVolumeResource1 = ImmutableLocalVolumeResource.builder()
                .sourcePath(TMP_1)
                .destinationPath(TEMPDIR_1)
                .isReadWrite(true)
                .build();

        LocalVolumeResource localVolumeResource2 = ImmutableLocalVolumeResource.builder()
                .sourcePath(TMP_2)
                .destinationPath(TEMPDIR_1)
                .isReadWrite(true)
                .build();

        localVolumeResources.clear();
        localVolumeResources.add(localVolumeResource1);
        localVolumeResources.add(localVolumeResource2);

        basicGreengrassHelper.createResourceDefinition(localDeviceResources, localVolumeResources, localS3Resources, localSageMakerResources, localSecretsManagerResources);
    }
}
