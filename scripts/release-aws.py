"""Release into an existing AWS stack after CI. Uses caller's scoped AWS identity."""
import json
import os
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def run(*args):
    return subprocess.run(args, cwd=ROOT, check=True, text=True, capture_output=True, timeout=900).stdout


def main():
    stack_name = os.environ['COMMERCE_STACK']
    tag = os.environ['COMMERCE_IMAGE_TAG']
    count = os.environ['COMMERCE_COUNT']
    service_role = os.environ['AWS_CLOUDFORMATION_ROLE_ARN']
    if not re.fullmatch(r'arn:aws(?:-us-gov|-cn)?:iam::[0-9]{12}:role/[A-Za-z0-9+=,.@_/-]+', service_role):
        raise ValueError('A valid CloudFormation service role ARN is required')
    if not re.fullmatch(r'[a-zA-Z][a-zA-Z0-9-]{0,127}', stack_name):
        raise ValueError('Invalid stack name')
    if not re.fullmatch(r'[0-9a-f]{40}', tag) or count not in {'1', '2', '3', '4'}:
        raise ValueError('Release requires a commit SHA and a task count from 1 to 4')
    stack = json.loads(run('aws', 'cloudformation', 'describe-stacks', '--stack-name', stack_name))['Stacks'][0]
    outputs = {item['OutputKey']: item['OutputValue'] for item in stack['Outputs']}
    for folder, output in [('backend', 'BackendImageRepository'), ('frontend', 'FrontendImageRepository'), ('ml-service', 'MlImageRepository')]:
        image = outputs[output] + ':' + tag
        # Immutable ECR tags allow retries of an interrupted multi-image release.
        repository = outputs[output].split('/', 1)[1]
        lookup = subprocess.run(['aws', 'ecr', 'describe-images', '--repository-name', repository,
                                 '--image-ids', 'imageTag=' + tag], capture_output=True, text=True, timeout=60)
        if lookup.returncode and 'ImageNotFoundException' not in lookup.stderr:
            raise RuntimeError('ECR lookup failed; release stopped: ' + lookup.stderr)
        if lookup.returncode:
            subprocess.run(['docker', 'build', '--tag', image, folder], cwd=ROOT, check=True)
            subprocess.run(['docker', 'push', image], cwd=ROOT, check=True)
    parameters = [{'ParameterKey': p['ParameterKey'], 'UsePreviousValue': True} for p in stack['Parameters']
                  if p['ParameterKey'] not in {'ImageTag', 'DesiredCount'}]
    parameters += [{'ParameterKey': 'ImageTag', 'ParameterValue': tag},
                   {'ParameterKey': 'DesiredCount', 'ParameterValue': count}]
    result = subprocess.run(['aws', 'cloudformation', 'update-stack', '--stack-name', stack_name,
        '--use-previous-template', '--role-arn', service_role, '--capabilities', 'CAPABILITY_IAM', '--parameters', json.dumps(parameters)],
        cwd=ROOT, capture_output=True, text=True)
    if result.returncode and 'No updates are to be performed' not in result.stderr:
        raise RuntimeError(result.stderr)
    if result.returncode == 0:
        run('aws', 'cloudformation', 'wait', 'stack-update-complete', '--stack-name', stack_name)
    run('aws', 'ecs', 'wait', 'services-stable', '--cluster', outputs['ClusterName'], '--services', outputs['ServiceName'])
    verify_deployment(outputs, tag, int(count))
    print('Release is stable and running the requested images:', stack_name, tag)


def verify_deployment(outputs, tag, count):
    result = json.loads(run('aws', 'ecs', 'describe-services', '--cluster', outputs['ClusterName'],
                            '--services', outputs['ServiceName']))
    if result.get('failures') or len(result.get('services', [])) != 1:
        raise RuntimeError('Unable to verify the released service')
    service = result['services'][0]
    if service['desiredCount'] != count or service['runningCount'] != count or service['pendingCount'] != 0:
        raise RuntimeError('Released service does not have the requested running task count')
    deployments = service.get('deployments', [])
    if len(deployments) != 1 or deployments[0].get('rolloutState') != 'COMPLETED':
        raise RuntimeError('Deployment has not completed successfully')
    task = json.loads(run('aws', 'ecs', 'describe-task-definition', '--task-definition', service['taskDefinition']))['taskDefinition']
    expected = {name: outputs[key] + ':' + tag for name, key in
                [('backend', 'BackendImageRepository'), ('frontend', 'FrontendImageRepository'), ('ml', 'MlImageRepository')]}
    actual = {container['name']: container['image'] for container in task['containerDefinitions']}
    if any(actual.get(name) != image for name, image in expected.items()):
        raise RuntimeError('Stable service is running a different release (possible rollback)')


if __name__ == '__main__':
    main()
