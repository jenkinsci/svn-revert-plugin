package jenkins.plugins.svn_revert;

import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.tasks.MailSender;
import hudson.tasks.Mailer;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import java.io.UnsupportedEncodingException;

class RevertMailSender extends MailSender {

    private final RevertMailFormatter formatter;
    private final BuildListener listener;

    RevertMailSender(final RevertMailFormatter formatter, final BuildListener listener) {
        super("", false, true, "UTF-8");
        this.formatter = formatter;
        this.listener = listener;
    }

    @Override
    protected MimeMessage getMail(final AbstractBuild<?, ?> build, final BuildListener listener)
            throws MessagingException, InterruptedException, UnsupportedEncodingException {
        final MimeMessage mail = super.getMail(build, listener);
        return formatter.format(mail, build, Mailer.descriptor().getUrl());
    }

    void sendRevertMail(final AbstractBuild<?, ?> build) throws InterruptedException {
        super.execute(build, listener);
    }

}
