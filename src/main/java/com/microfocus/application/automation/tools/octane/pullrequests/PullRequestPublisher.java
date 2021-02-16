/*
 * Certain versions of software and/or documents ("Material") accessible here may contain branding from
 * Hewlett-Packard Company (now HP Inc.) and Hewlett Packard Enterprise Company.  As of September 1, 2017,
 * the Material is now offered by Micro Focus, a separately owned and operated company.  Any reference to the HP
 * and Hewlett Packard Enterprise/HPE marks is historical in nature, and the HP and Hewlett Packard Enterprise/HPE
 * marks are the property of their respective owners.
 * __________________________________________________________________
 * MIT License
 *
 * (c) Copyright 2012-2021 Micro Focus or one of its affiliates.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software,
 * and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 * ___________________________________________________________________
 */

package com.microfocus.application.automation.tools.octane.pullrequests;

import com.cloudbees.plugins.credentials.CredentialsMatcher;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.hp.octane.integrations.OctaneClient;
import com.hp.octane.integrations.OctaneSDK;
import com.hp.octane.integrations.dto.scm.PullRequest;
import com.hp.octane.integrations.exceptions.OctaneValidationException;
import com.hp.octane.integrations.services.pullrequestsandbranches.PullRequestAndBranchService;
import com.hp.octane.integrations.services.pullrequestsandbranches.factory.FetchFactory;
import com.hp.octane.integrations.services.pullrequestsandbranches.factory.FetchHandler;
import com.hp.octane.integrations.services.pullrequestsandbranches.factory.PullRequestFetchParameters;
import com.hp.octane.integrations.services.pullrequestsandbranches.rest.ScmTool;
import com.hp.octane.integrations.services.pullrequestsandbranches.rest.authentication.AuthenticationStrategy;
import com.microfocus.application.automation.tools.octane.GitFetchUtils;
import com.microfocus.application.automation.tools.octane.JellyUtils;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.ListBoxModel;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import java.util.function.Consumer;

/**
 * Post-build action of Uft test detection
 */

public class PullRequestPublisher extends Recorder implements SimpleBuildStep {
    private String configurationId;
    private String workspaceId;
    private String repositoryUrl;
    private String credentialsId;
    private String sourceBranchFilter;
    private String targetBranchFilter;
    private String scmTool;

    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public PullRequestPublisher(String configurationId, String workspaceId, String scmTool, String repositoryUrl, String credentialsId, String sourceBranchFilter, String targetBranchFilter) {
        this.configurationId = JellyUtils.NONE.equalsIgnoreCase(configurationId) ? null : configurationId;
        this.workspaceId = JellyUtils.NONE.equalsIgnoreCase(workspaceId) ? null : workspaceId;
        this.repositoryUrl = repositoryUrl;
        this.credentialsId = credentialsId;
        this.sourceBranchFilter = sourceBranchFilter;
        this.targetBranchFilter = targetBranchFilter;
        this.scmTool = JellyUtils.NONE.equalsIgnoreCase(scmTool) ? null : scmTool;
    }

    @Override
    public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath filePath, @Nonnull Launcher launcher, @Nonnull TaskListener taskListener) throws InterruptedException, IOException {
        performInternal(run, taskListener);
    }

    @Override
    public boolean perform(AbstractBuild build, @Nonnull Launcher launcher, BuildListener listener) {
        performInternal(build, listener);
        return build.getResult() == Result.SUCCESS;
    }

    public void performInternal(@Nonnull Run<?, ?> run, @Nonnull TaskListener taskListener) {
        LogConsumer logConsumer = new LogConsumer(taskListener.getLogger());
        logConsumer.printLog("PullRequestPublisher is started ***********************************************************************");
        if (configurationId == null) {
            throw new IllegalArgumentException("ALM Octane configuration is not defined.");
        }
        if (workspaceId == null) {
            throw new IllegalArgumentException("ALM Octane workspace is not defined.");
        }
        if (scmTool == null) {
            throw new IllegalArgumentException("SCM Tool is not defined.");
        }

        String myCredentialsId = credentialsId;
        String myConfigurationId = configurationId;
        String myWorkspaceId = workspaceId;
        String myScmTool = scmTool;
        try {
            EnvVars env = run.getEnvironment(taskListener);
            myCredentialsId = env.expand(credentialsId);
            myConfigurationId = env.expand(configurationId);
            myWorkspaceId = env.expand(workspaceId);
            myScmTool = env.expand(scmTool);
        } catch (IOException | InterruptedException e) {
            taskListener.error("Failed loading build environment " + e);
        }

        PullRequestFetchParameters fp = createFetchParameters(run, taskListener, myConfigurationId, myWorkspaceId, logConsumer::printLog);

        StandardCredentials credentials = GitFetchUtils.getCredentialsById(myCredentialsId, run, taskListener.getLogger());
        AuthenticationStrategy authenticationStrategy = GitFetchUtils.getAuthenticationStrategy(credentials);

        FetchHandler fetchHandler = FetchFactory.getHandler(ScmTool.fromValue(myScmTool), authenticationStrategy);
        try {
            OctaneClient octaneClient = OctaneSDK.getClientByInstanceId(myConfigurationId);
            logConsumer.printLog("ALM Octane " + octaneClient.getConfigurationService().getConfiguration().geLocationForLog());
            octaneClient.validateOctaneIsActiveAndSupportVersion(PullRequestAndBranchService.PULL_REQUEST_COLLECTION_SUPPORTED_VERSION);
            List<PullRequest> pullRequests = fetchHandler.fetchPullRequests(fp, GitFetchUtils::getUserIdForCommit, logConsumer::printLog);

            synchronized (run) {
                long index = run.getActions(PullRequestBuildAction.class).size();
                PullRequestBuildAction buildAction = new PullRequestBuildAction(run, pullRequests, fp.getRepoUrl(), fp.getMinUpdateTime(),
                        fp.getSourceBranchFilter(), fp.getTargetBranchFilter(), index);
                run.addAction(buildAction);
            }

            if (!pullRequests.isEmpty()) {
                octaneClient.getPullRequestAndBranchService().sendPullRequests(pullRequests, myWorkspaceId, fp, logConsumer::printLog);
            }
            //update templates
            GitFetchUtils.updateRepoTemplates(octaneClient.getPullRequestAndBranchService(), fetchHandler, getRepositoryUrl(),
                    Long.parseLong(myWorkspaceId), logConsumer::printLog);
        } catch (OctaneValidationException e) {
            logConsumer.printLog("ALM Octane pull request collector failed on validation : " + e.getMessage());
            run.setResult(Result.FAILURE);
        } catch (Exception e) {
            logConsumer.printLog("ALM Octane pull request collector failed : " + e.getMessage());
            e.printStackTrace(taskListener.getLogger());
            run.setResult(Result.FAILURE);
        }
    }

    private PullRequestFetchParameters createFetchParameters(@Nonnull Run<?, ?> run, @Nonnull TaskListener taskListener, String myConfigurationId, String myWorkspaceId, Consumer<String> logConsumer) {

        PullRequestFetchParameters fp;
        try {
            EnvVars env = run.getEnvironment(taskListener);
            fp = new PullRequestFetchParameters()
                    .setRepoUrl(env.expand(repositoryUrl))
                    .setSourceBranchFilter(env.expand(sourceBranchFilter))
                    .setTargetBranchFilter(env.expand(targetBranchFilter));
        } catch (IOException | InterruptedException e) {
            taskListener.error("Failed loading build environment " + e);
            fp = new PullRequestFetchParameters()
                    .setRepoUrl(repositoryUrl)
                    .setSourceBranchFilter(sourceBranchFilter)
                    .setTargetBranchFilter(targetBranchFilter);
        }

        ParametersAction parameterAction = run.getAction(ParametersAction.class);
        if (parameterAction != null) {
            fp.setPageSize(getIntegerValueParameter(parameterAction, "pullrequests_page_size"));
            fp.setMaxPRsToFetch(getIntegerValueParameter(parameterAction, "pullrequests_max_pr_to_collect"));
            fp.setMaxCommitsToFetch(getIntegerValueParameter(parameterAction, "pullrequests_max_commits_to_collect"));
            fp.setMinUpdateTime(getLongValueParameter(parameterAction, "pullrequests_min_update_time"));
        }
        if (fp.getMinUpdateTime() == PullRequestFetchParameters.DEFAULT_MIN_UPDATE_DATE) {
            long lastUpdateTime = OctaneSDK.getClientByInstanceId(myConfigurationId).getPullRequestAndBranchService().getPullRequestLastUpdateTime(myWorkspaceId, fp.getRepoUrl());
            fp.setMinUpdateTime(lastUpdateTime);
        }

        logConsumer.accept("Repository URL        : " + fp.getRepoUrl());
        logConsumer.accept("Min update date       : " + fp.getMinUpdateTime());
        logConsumer.accept("Source branch filter  : " + fp.getSourceBranchFilter());
        logConsumer.accept("Target branch filter  : " + fp.getTargetBranchFilter());
        logConsumer.accept("Max PRs to collect    : " + fp.getMaxPRsToFetch());
        logConsumer.accept("Max commits to collect: " + fp.getMaxCommitsToFetch());
        return fp;
    }

    private Integer getIntegerValueParameter(ParametersAction parameterAction, String paramValue) {
        ParameterValue pv = parameterAction.getParameter(paramValue);
        if (pv != null && pv.getValue() instanceof String) {
            try {
                return Integer.valueOf((String) pv.getValue());
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private Long getLongValueParameter(ParametersAction parameterAction, String paramValue) {
        ParameterValue pv = parameterAction.getParameter(paramValue);
        if (pv != null && pv.getValue() instanceof String) {
            try {
                return Long.valueOf((String) pv.getValue());
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    public String getConfigurationId() {
        return configurationId;
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    private static class LogConsumer {

        private final PrintStream ps;

        public LogConsumer(PrintStream ps) {
            this.ps = ps;
        }

        public void printLog(String msg) {
            ps.println("PullRequestPublisher : " + msg);
        }
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    public String getRepositoryUrl() {
        return repositoryUrl;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    public String getSourceBranchFilter() {
        return sourceBranchFilter;
    }

    public String getTargetBranchFilter() {
        return targetBranchFilter;
    }

    public String getScmTool() {
        return scmTool;
    }

    @Symbol("collectPullRequestsToAlmOctane")
    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        static final CredentialsMatcher CREDENTIALS_MATCHER = CredentialsMatchers.anyOf(new CredentialsMatcher[]{
                CredentialsMatchers.instanceOf(StandardUsernamePasswordCredentials.class),
                CredentialsMatchers.instanceOf(StringCredentials.class)});

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item project,
                                                     @QueryParameter String credentialsId) {
            return JellyUtils.fillCredentialsIdItems(project, credentialsId, CREDENTIALS_MATCHER);
        }

        public ListBoxModel doFillScmToolItems() {
            ListBoxModel m = JellyUtils.createComboModelWithNoneValue();
            for (ScmTool tool : ScmTool.values()) {
                m.add(tool.getDesc(), tool.getValue());
            }

            return m;
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        public ListBoxModel doFillConfigurationIdItems() {
            return JellyUtils.fillConfigurationIdModel();
        }

        public ListBoxModel doFillWorkspaceIdItems(@QueryParameter String configurationId, @QueryParameter(value = "workspaceId") String workspaceId) {
            return JellyUtils.fillWorkspaceModel(configurationId, workspaceId);
        }

        public String getDisplayName() {
            return "ALM Octane pull-request collector";
        }
    }
}
