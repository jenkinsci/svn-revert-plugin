package jenkins.plugins.svn_revert;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;

import java.io.IOException;

import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

public class JenkinsGlue extends Notifier {

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.BUILD;
    }

    @DataBoundConstructor
    public JenkinsGlue() {
    }

    @Override
    public SvnRevertDescriptorImpl getDescriptor() {
        return (SvnRevertDescriptorImpl)super.getDescriptor();
    }

    @Override
    public boolean perform(final AbstractBuild<?, ?> build,
            final Launcher launcher, final BuildListener listener)
    throws InterruptedException, IOException {
        final Messenger messenger = new Messenger(listener.getLogger());
        final ChangedRevisions changedRevisions = new ChangedRevisions(build);
        final ChangedFiles changedFiles = new ChangedFiles(build);
        final ModuleFinder locationFinder = new ModuleFinder(build, listener);
        final SvnReverter svnReverter = new SvnReverter(build, messenger, new SvnKitClientFactory(),
                locationFinder, changedRevisions);
        final Claimer claimer = new Claimer(changedRevisions, isClaimPluginPresent());
        final RevertMailSender mailer = new RevertMailSender(new RevertMailFormatter(changedRevisions), listener);
        final ChangeLocator changeLocator = new ChangeLocator(build, locationFinder, changedFiles );
        final CommitMessages commitMessages = new CommitMessages(build);
        final CommitCountRule commitCountRule = new CommitCountRule(build, getDescriptor().isRevertMultipleCommits());
        return Bouncer.throwOutIfUnstable(build, launcher, messenger, svnReverter, claimer, changeLocator, commitMessages, mailer, commitCountRule);
    }

    private boolean isClaimPluginPresent() {
        return isPluginPresent("claim");
    }

    private boolean isPluginPresent(final String pluginName) {
        return Jenkins.get().getPlugin(pluginName) != null;
    }

    @Extension(optional = true)
    public static final class SvnRevertDescriptorImpl extends BuildStepDescriptor<Publisher> {

        private boolean revertMultipleCommits;

        public SvnRevertDescriptorImpl() {
            revertMultipleCommits = true;
            load();
        }

        @Override
        public boolean configure(final StaplerRequest req, final JSONObject formData) throws FormException {
            revertMultipleCommits = formData.containsKey("revertMultipleCommits");
            save();
            return super.configure(req, formData);
        }

        @Override
        public boolean isApplicable(final Class<? extends AbstractProject> arg0) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Reverts commits that breaks the build";
        }

        public boolean isRevertMultipleCommits() {
            return revertMultipleCommits;
        }

        public void setRevertMultipleCommits(final boolean newValue) {
            revertMultipleCommits = newValue;
            save();
        }

    }

}
