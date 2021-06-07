import org.pharmgkb.jenkins.ShouldBuildJob


void call() {
  echo "JOB_NAME: ${env.JOB_NAME}"
  echo "BRANCH_NAME: ${env.BRANCH_NAME}"
  ShouldBuildJob shouldBuild = new ShouldBuildJob(this);
  if (!shouldBuild.becauseDependabot() ||
      !shouldBuild.becausePullRequest() ||
      !shouldBuild.becauseSkipCi() ||
      !shouldBuild.becauseOutdated()) {
    return
  }
}
