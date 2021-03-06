/*************************GO-LICENSE-START*********************************
 * Copyright 2014 ThoughtWorks, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *************************GO-LICENSE-END***********************************/

package com.thoughtworks.go.domain;


import java.util.List;
import java.util.Map;
import java.util.Set;

import com.thoughtworks.go.config.CaseInsensitiveString;
import com.thoughtworks.go.config.JobConfig;
import com.thoughtworks.go.config.JobConfigs;
import com.thoughtworks.go.config.PipelineConfig;
import com.thoughtworks.go.config.PipelineConfigs;
import com.thoughtworks.go.config.exceptions.PipelineGroupNotFoundException;
import com.thoughtworks.go.config.materials.MaterialConfigs;
import com.thoughtworks.go.config.materials.PackageMaterialConfig;
import com.thoughtworks.go.config.materials.ScmMaterialConfig;
import com.thoughtworks.go.config.materials.git.GitMaterialConfig;
import com.thoughtworks.go.config.materials.mercurial.HgMaterialConfig;
import com.thoughtworks.go.config.materials.perforce.P4MaterialConfig;
import com.thoughtworks.go.config.materials.svn.SvnMaterialConfig;
import com.thoughtworks.go.config.materials.tfs.TfsMaterialConfig;
import com.thoughtworks.go.domain.materials.MaterialConfig;
import com.thoughtworks.go.helper.MaterialConfigsMother;
import com.thoughtworks.go.helper.PipelineConfigMother;
import com.thoughtworks.go.security.GoCipher;
import com.thoughtworks.go.util.Pair;
import com.thoughtworks.go.util.command.UrlArgument;
import org.junit.Test;

import static com.thoughtworks.go.helper.PipelineConfigMother.createGroup;
import static com.thoughtworks.go.helper.PipelineConfigMother.createPipelineConfig;
import static java.util.Arrays.asList;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsCollectionContaining.hasItem;
import static org.hamcrest.core.IsCollectionContaining.hasItems;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;

public class PipelineGroupsTest {

    @Test
    public void shouldOnlySavePipelineToTargetGroup() {
        PipelineConfigs defaultGroup = createGroup("defaultGroup", createPipelineConfig("pipeline1", "stage1"));
        PipelineConfigs defaultGroup2 = createGroup("defaultGroup2", createPipelineConfig("pipeline2", "stage2"));
        PipelineGroups pipelineGroups = new PipelineGroups(defaultGroup, defaultGroup2);
        PipelineConfig pipelineConfig = createPipelineConfig("pipeline3", "stage1");

        pipelineGroups.addPipeline("defaultGroup", pipelineConfig);

        assertThat(defaultGroup, hasItem(pipelineConfig));
        assertThat(defaultGroup2, not(hasItem(pipelineConfig)));
        assertThat(pipelineGroups.size(), is(2));
    }

    @Test
    public void shouldSaveNewPipelineGroupOnTheTop() {
        PipelineConfigs defaultGroup = createGroup("defaultGroup", createPipelineConfig("pipeline1", "stage1"));
        PipelineConfigs defaultGroup2 = createGroup("defaultGroup2", createPipelineConfig("pipeline2", "stage2"));
        PipelineGroups pipelineGroups = new PipelineGroups(defaultGroup, defaultGroup2);

        PipelineConfig pipelineConfig = createPipelineConfig("pipeline3", "stage1");

        pipelineGroups.addPipeline("defaultGroup3", pipelineConfig);

        PipelineConfigs group = createGroup("defaultGroup3", pipelineConfig);
        assertThat(pipelineGroups.indexOf(group), is(0));
    }

    @Test
    public void validate_shouldMarkDuplicatePipelineGroupNamesAsError() {
        PipelineConfigs first = createGroup("first", "pipeline");
        PipelineConfigs dup = createGroup("first", "pipeline");
        PipelineGroups groups = new PipelineGroups(first, dup);
        groups.validate(null);
        assertThat(first.errors().on(PipelineConfigs.GROUP), is("Group with name 'first' already exists"));
        assertThat(dup.errors().on(PipelineConfigs.GROUP), is("Group with name 'first' already exists"));
    }

    @Test
    public void shouldReturnTrueWhenGroupNameIsEmptyAndDefaultGroupExists() {
        PipelineConfig existingPipeline = createPipelineConfig("pipeline1", "stage1");
        PipelineConfigs defaultGroup = createGroup("defaultGroup", existingPipeline);
        PipelineGroups pipelineGroups = new PipelineGroups(defaultGroup);
        PipelineConfig newPipeline = createPipelineConfig("pipeline3", "stage1");

        pipelineGroups.addPipeline("", newPipeline);

        assertThat(pipelineGroups.size(), is(1));
        assertThat(defaultGroup, hasItem(existingPipeline));
        assertThat(defaultGroup, hasItem(newPipeline));
    }

    @Test
    public void shouldErrorOutIfDuplicatePipelineIsAdded() {
        PipelineConfig pipeline1 = createPipelineConfig("pipeline1", "stage1");
        PipelineConfig pipeline2 = createPipelineConfig("pipeline1", "stage1");
        PipelineConfigs defaultGroup = createGroup("defaultGroup", pipeline1);
        PipelineConfigs anotherGroup = createGroup("anotherGroup", pipeline2);
        PipelineGroups pipelineGroups = new PipelineGroups(defaultGroup, anotherGroup);

        pipelineGroups.validate(null);

        assertThat(pipeline1.errors().isEmpty(), is(false));
        assertThat(pipeline1.errors().on(PipelineConfig.NAME), is("You have defined multiple pipelines named 'pipeline1'. Pipeline names must be unique."));
        assertThat(pipeline2.errors().isEmpty(), is(false));
        assertThat(pipeline2.errors().on(PipelineConfig.NAME), is("You have defined multiple pipelines named 'pipeline1'. Pipeline names must be unique."));
    }

    @Test
    public void shouldErrorOutIfDuplicatePipelineIsAddedToSameGroup() {
        PipelineConfig pipeline1 = createPipelineConfig("pipeline1", "stage1");
        PipelineConfig pipeline2 = createPipelineConfig("pipeline1", "stage1");
        PipelineConfigs defaultGroup = createGroup("defaultGroup", pipeline1, pipeline2);
        PipelineGroups pipelineGroups = new PipelineGroups(defaultGroup);

        pipelineGroups.validate(null);

        assertThat(pipeline1.errors().isEmpty(), is(false));
        assertThat(pipeline1.errors().on(PipelineConfig.NAME), is("You have defined multiple pipelines named 'pipeline1'. Pipeline names must be unique."));
        assertThat(pipeline2.errors().isEmpty(), is(false));
        assertThat(pipeline2.errors().on(PipelineConfig.NAME), is("You have defined multiple pipelines named 'pipeline1'. Pipeline names must be unique."));
    }

    @Test
    public void shouldFindAPipelineGroupByName() {
        PipelineConfig pipeline = createPipelineConfig("pipeline1", "stage1");
        PipelineConfigs defaultGroup = createGroup("defaultGroup", pipeline);
        PipelineGroups pipelineGroups = new PipelineGroups(defaultGroup);

        assertThat(pipelineGroups.findGroup("defaultGroup"), is(defaultGroup));
    }

    @Test(expected = PipelineGroupNotFoundException.class)
    public void shouldThrowGroupNotFoundExceptionWhenSearchingForANonExistingGroup() {
        PipelineConfig pipeline = createPipelineConfig("pipeline1", "stage1");
        PipelineConfigs defaultGroup = createGroup("defaultGroup", pipeline);
        PipelineGroups pipelineGroups = new PipelineGroups(defaultGroup);

        pipelineGroups.findGroup("NonExistantGroup");
    }

    @Test
    public void shouldReturnAllUniqueSchedulableScmMaterials() {
        final MaterialConfig svnMaterialConfig = new SvnMaterialConfig("http://svn_url_1", "username", "password", false);
        ((ScmMaterialConfig) svnMaterialConfig).setAutoUpdate(false);
        final MaterialConfig svnMaterialConfigWithAutoUpdate = new SvnMaterialConfig("http://svn_url_2", "username", "password", false);
        ((ScmMaterialConfig) svnMaterialConfigWithAutoUpdate).setAutoUpdate(true);
        final MaterialConfig hgMaterialConfig = new HgMaterialConfig("http://hg_url", null);
        ((ScmMaterialConfig) hgMaterialConfig).setAutoUpdate(false);
        final MaterialConfig gitMaterialConfig = new GitMaterialConfig("http://git_url");
        ((ScmMaterialConfig) gitMaterialConfig).setAutoUpdate(false);
        final MaterialConfig tfsMaterialConfig = new TfsMaterialConfig(mock(GoCipher.class), new UrlArgument("http://tfs_url"), "username", "domain", "password", "project_path");
        ((ScmMaterialConfig) tfsMaterialConfig).setAutoUpdate(false);
        final MaterialConfig p4MaterialConfig = new P4MaterialConfig("http://p4_url", "view", "username");
        ((ScmMaterialConfig) p4MaterialConfig).setAutoUpdate(false);
        final MaterialConfig dependencyMaterialConfig = MaterialConfigsMother.dependencyMaterialConfig();
        final PipelineConfig p1 = PipelineConfigMother.pipelineConfig("pipeline1", new MaterialConfigs(svnMaterialConfig), new JobConfigs(new JobConfig(new CaseInsensitiveString("jobName"))));
        final PipelineConfig p2 = PipelineConfigMother.pipelineConfig("pipeline2", new MaterialConfigs(svnMaterialConfig, gitMaterialConfig),
                new JobConfigs(new JobConfig(new CaseInsensitiveString("jobName"))));
        final PipelineConfig p3 = PipelineConfigMother.pipelineConfig("pipeline3", new MaterialConfigs(hgMaterialConfig, dependencyMaterialConfig),
                new JobConfigs(new JobConfig(new CaseInsensitiveString("jobName"))));
        final PipelineConfig p4 = PipelineConfigMother.pipelineConfig("pipeline4", new MaterialConfigs(p4MaterialConfig), new JobConfigs(new JobConfig(new CaseInsensitiveString("jobName"))));
        final PipelineConfig p5 = PipelineConfigMother.pipelineConfig("pipeline5", new MaterialConfigs(svnMaterialConfigWithAutoUpdate, tfsMaterialConfig),
                new JobConfigs(new JobConfig(new CaseInsensitiveString("jobName"))));
        final PipelineGroups groups = new PipelineGroups(new PipelineConfigs(p1, p2, p3, p4, p5));

        final Set<MaterialConfig> materials = groups.getAllUniquePostCommitSchedulableMaterials();

        assertThat(materials.size(), is(5));
        assertThat(materials, hasItems(svnMaterialConfig, hgMaterialConfig, gitMaterialConfig, tfsMaterialConfig, p4MaterialConfig));
        assertThat(materials, not(hasItem(svnMaterialConfigWithAutoUpdate)));
    }

    @Test
    public void shouldGetPackageUsageInPipelines() throws Exception {

        PackageMaterialConfig packageOne = new PackageMaterialConfig("package-id-one");
        PackageMaterialConfig packageTwo = new PackageMaterialConfig("package-id-two");
        final PipelineConfig p1 = PipelineConfigMother.pipelineConfig("pipeline1", new MaterialConfigs(packageOne, packageTwo), new JobConfigs(new JobConfig(new CaseInsensitiveString("jobName"))));
        final PipelineConfig p2 = PipelineConfigMother.pipelineConfig("pipeline2", new MaterialConfigs(packageTwo), new JobConfigs(new JobConfig(new CaseInsensitiveString("jobName"))));

        PipelineGroups groups = new PipelineGroups();
        PipelineConfigs groupOne = new PipelineConfigs(p1);
        PipelineConfigs groupTwo = new PipelineConfigs(p2);
        groups.addAll(asList(groupOne, groupTwo));

        Map<String, List<Pair<PipelineConfig,PipelineConfigs>>> packageToPipelineMap = groups.getPackageUsageInPipelines();

        assertThat(packageToPipelineMap.get("package-id-one").size(), is(1));
        assertThat(packageToPipelineMap.get("package-id-one"), hasItems(new Pair<PipelineConfig,PipelineConfigs>(p1,groupOne)));
        assertThat(packageToPipelineMap.get("package-id-two").size(), is(2));
        assertThat(packageToPipelineMap.get("package-id-two"), hasItems(new Pair<PipelineConfig,PipelineConfigs>(p1,groupOne), new Pair<PipelineConfig,PipelineConfigs>(p2,groupTwo)));
    }

    @Test
    public void shouldComputePackageUsageInPipelinesOnlyOnce() throws Exception {

        PackageMaterialConfig packageOne = new PackageMaterialConfig("package-id-one");
        PackageMaterialConfig packageTwo = new PackageMaterialConfig("package-id-two");
        final PipelineConfig p1 = PipelineConfigMother.pipelineConfig("pipeline1", new MaterialConfigs(packageOne, packageTwo), new JobConfigs(new JobConfig(new CaseInsensitiveString("jobName"))));
        final PipelineConfig p2 = PipelineConfigMother.pipelineConfig("pipeline2", new MaterialConfigs(packageTwo), new JobConfigs(new JobConfig(new CaseInsensitiveString("jobName"))));

        PipelineGroups groups = new PipelineGroups();
        groups.addAll(asList(new PipelineConfigs(p1), new PipelineConfigs(p2)));

        Map<String, List<Pair<PipelineConfig,PipelineConfigs>>> result1 = groups.getPackageUsageInPipelines();
        Map<String, List<Pair<PipelineConfig,PipelineConfigs>>> result2 = groups.getPackageUsageInPipelines();
        assertSame(result1, result2);
    }


}
