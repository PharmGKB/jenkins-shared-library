import org.pharmgkb.jenkins.ShouldBuildJob


void call() {
  echo "JOB_NAME: ${env.JOB_NAME}"
  ShouldBuildJob shouldBuild = new ShouldBuildJob(this);
  if (!shouldBuild.becauseDependabot() ||
      !shouldBuild.becausePullRequest() ||
      !shouldBuild.becauseSkipCi() ||
      !shouldBuild.becauseOutdated()) {
    return
  }
}
