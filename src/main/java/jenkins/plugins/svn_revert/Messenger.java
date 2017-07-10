package jenkins.plugins.svn_revert;

import java.io.PrintStream;

import org.tmatesoft.svn.core.SVNException;

class Messenger {


    static final String UNSTABLE = "UNSTABLE";
    static final String BUILD_STATUS_NOT_UNSTABLE =
            "Will not revert since build status is not UNSTABLE.";
    static final String BUILD_STATUS_SUCCESS =
            "Will not revert since build status is SUCCESS";            
    static final String PREVIOUS_BUILD_STATUS_NOT_SUCCESS =
            "Will not revert since previous build status is not SUCCESS.";
    static final String NOT_SUBVERSION_SCM =
            "The Subversion Revert Plugin can only be used with Subversion SCM.";
    static final String NO_SVN_AUTH_PROVIDER = "No Subversion credentials available.";
    static final String REVERTED_CHANGES =
            "Reverted changes between %d:%d in %s since build became %s.\n";
    static final String NO_CHANGES =
            "Will not revert since there are no changes in current build.";
    static final String FILES_TO_REVERT_OUT_OF_DATE =
            "Tried to revert since build status became UNSTABLE, " +
            "but failed since files to revert are out of date.";
    static final String CHANGES_OUTSIDE_WORKSPACE =
            "Will not revert since some changes in commit(s) outside workspace detected.";
    static final String SUBVERSION_EXCEPTION_DURING_REVERT =
            "Revert failed because of a Subversion error:";
    static final String SUBVERSION_ERROR_CODE = "Subversion Error Code: ";
    static final String COMMIT_MESSAGE_CONTAINS =
            "Will not revert since commit message contains '%s'.";
    static final String TOO_MANY_CHANGES =
            "Will not revert since there are multiple commits in the failing build.";
    private final PrintStream logger;

    Messenger(final PrintStream logger) {
        this.logger = logger;
    }

    void informBuildStatusSuccess() {
        logger.println(BUILD_STATUS_SUCCESS);
    }    
    
    void informBuildStatusNotUnstable() {
        logger.println(BUILD_STATUS_NOT_UNSTABLE);
    }

    void informPreviousBuildStatusNotSuccess() {
        logger.println(PREVIOUS_BUILD_STATUS_NOT_SUCCESS);
    }

    void informNotSubversionSCM() {
        logger.println(NOT_SUBVERSION_SCM);
    }

    void informNoSvnAuthProvider() {
        logger.println(NO_SVN_AUTH_PROVIDER);
    }

    void informReverted(final Revisions revisions, final String repository) {
        informReverted(revisions, repository, UNSTABLE);
    }
    
    void informReverted(final Revisions revisions, final String repository, final String status) {
        logger.format(REVERTED_CHANGES, revisions.getBefore(), revisions.getLast(), repository, status);
    }

    void informNoChanges() {
        logger.println(NO_CHANGES);
    }

    void informCommitMessageContains(final String substring) {
        logger.format(COMMIT_MESSAGE_CONTAINS, substring);
    }

    void informFilesToRevertOutOfDate() {
        logger.println(FILES_TO_REVERT_OUT_OF_DATE);
    }

    void informChangesOutsideWorkspace() {
        logger.println(CHANGES_OUTSIDE_WORKSPACE);
    }

    void informNothingRevertedBecauseOf(final SVNException exception) {
        logger.println(SUBVERSION_EXCEPTION_DURING_REVERT);
        logger.println(SUBVERSION_ERROR_CODE + exception.getErrorMessage().getErrorCode());
        printStackTraceFor(exception);
    }

    void printStackTraceFor(final Exception exception) {
        exception.printStackTrace(logger);
    }

    void log(final String string) {
        logger.println(string);
    }

    public void informTooManyChanges() {
        logger.println(TOO_MANY_CHANGES);
    }


}
