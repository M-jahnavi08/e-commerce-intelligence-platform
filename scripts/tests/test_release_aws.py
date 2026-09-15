import importlib.util
import json
from pathlib import Path
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("release", Path(__file__).resolve().parents[1] / "release-aws.py")
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)

class ReleaseTests(unittest.TestCase):
    def setUp(self):
        self.outputs = dict(ClusterName="cluster", ServiceName="service", BackendImageRepository="ecr/backend", FrontendImageRepository="ecr/frontend", MlImageRepository="ecr/ml")
        self.service = dict(desiredCount=2, runningCount=2, pendingCount=0, deployments=[dict(rolloutState="COMPLETED")], taskDefinition="task:2")
        self.task = dict(containerDefinitions=[dict(name=n, image="ecr/" + n + ":" + "a" * 40) for n in ["backend", "frontend", "ml"]])

    def verify(self):
        with patch.object(release, "run", side_effect=[json.dumps(dict(services=[self.service])), json.dumps(dict(taskDefinition=self.task))]):
            release.verify_deployment(self.outputs, "a" * 40, 2)

    def test_matching_release_passes(self):
        self.verify()

    def test_stable_rollback_is_rejected(self):
        self.task["containerDefinitions"][0]["image"] = "ecr/backend:old"
        with self.assertRaisesRegex(RuntimeError, "different release"):
            self.verify()

    def test_missing_tasks_are_rejected(self):
        self.service["runningCount"] = 1
        with self.assertRaisesRegex(RuntimeError, "task count"):
            self.verify()

    def test_incomplete_rollout_is_rejected(self):
        self.service["deployments"][0]["rolloutState"] = "FAILED"
        with self.assertRaisesRegex(RuntimeError, "not completed"):
            self.verify()

    def test_noop_release_preserves_parameters_and_verifies_images(self):
        import subprocess
        stack = dict(Outputs=[dict(OutputKey=k, OutputValue=v) for k,v in self.outputs.items()], Parameters=[dict(ParameterKey="RuntimeSecretArn", ParameterValue="secret-arn"), dict(ParameterKey="ImageTag", ParameterValue="old")])
        responses = [json.dumps(dict(Stacks=[stack])), "", json.dumps(dict(services=[self.service])), json.dumps(dict(taskDefinition=self.task))]
        with patch.dict(release.os.environ, dict(AWS_CLOUDFORMATION_ROLE_ARN="arn:aws:iam::123456789012:role/CommerceCloudFormation", COMMERCE_STACK="commerce", COMMERCE_IMAGE_TAG="a"*40, COMMERCE_COUNT="2")), patch.object(release, "run", side_effect=responses), patch.object(release.subprocess, "run", side_effect=[subprocess.CompletedProcess([],0,"","")]*3 + [subprocess.CompletedProcess([],1,"","No updates are to be performed")]) as command:
            release.main()
            update = command.call_args.args[0]
            params = json.loads(update[update.index("--parameters") + 1])
            self.assertIn(dict(ParameterKey="RuntimeSecretArn", UsePreviousValue=True), params)
            self.assertIn("--role-arn", update)
            self.assertEqual(command.call_count, 4)

    def test_ecr_access_denied_does_not_build(self):
        import subprocess
        stack = dict(Outputs=[dict(OutputKey=k, OutputValue=v) for k, v in self.outputs.items()])
        with patch.dict(release.os.environ, dict(AWS_CLOUDFORMATION_ROLE_ARN="arn:aws:iam::123456789012:role/CommerceCloudFormation", COMMERCE_STACK="commerce", COMMERCE_IMAGE_TAG="a"*40, COMMERCE_COUNT="2")), patch.object(release, "run", return_value=json.dumps(dict(Stacks=[stack]))), patch.object(release.subprocess, "run", return_value=subprocess.CompletedProcess([], 1, "", "AccessDeniedException")) as command:
            with self.assertRaisesRegex(RuntimeError, "ECR lookup failed"):
                release.main()
            self.assertEqual(command.call_count, 1)

if __name__ == "__main__":
    unittest.main()
