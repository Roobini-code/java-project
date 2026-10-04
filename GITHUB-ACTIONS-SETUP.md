# GitHub Actions CI/CD setup for Taskboard

This checklist explains what the Taskboard GitHub Actions pipeline does, what
you need to configure, and which repository or service to configure it in.

## What the pipeline does

The application workflow is
[`.github/workflows/taskboard.yml`](.github/workflows/taskboard.yml). It calls
the reusable workflow stored in the separate
[`Roobini-code/ci-cd-pipelines`](https://github.com/Roobini-code/ci-cd-pipelines)
repository.

| Trigger | Pipeline behavior |
| --- | --- |
| Pull request targeting `main` | Checks out the app, installs Java 21, runs `mvn clean verify`, and builds the Docker image. It does not push an image or deploy. |
| Push to `main` (including a merge) | Repeats verification, publishes a versioned Docker image and `latest`, deploys the versioned image to EC2, checks the app's HTTP response, then creates a Git tag. |

For example, if the Maven version is `1.0.0`, an Actions run may publish
`roobinidevops/taskboard-java:1.0.0-42.1` and `:latest`, then create the Git tag
`v1.0.0-42.1`. The run number and attempt make each deployment version unique.
The deployment reuses the EC2 Docker volume `taskboard-data` to preserve tasks.

**Important:** every push to `main` triggers a deployment. Protect `main` and
require pull requests if deployments must only follow reviewed changes. Fork
pull requests do not receive repository secrets and cannot publish or deploy.

## Actions to take

### 1. Configure the application repository

In the GitHub `java-project` repository:

1. Open **Settings → Secrets and variables → Actions**.
2. Choose **New repository secret** and add each secret below. Secret names
   must match exactly.

| Secret name | What to put in it |
| --- | --- |
| `DOCKERHUB_USERNAME` | Docker Hub account username that can publish to `roobinidevops/taskboard-java` (currently `roobinidevops`). |
| `DOCKERHUB_TOKEN` | Docker Hub access token with **Read & Write** permission. Do not use the Docker Hub account password. |
| `EC2_HOST` | EC2 public DNS name or IPv4 address, without `http://`, `https://`, or a port. |
| `EC2_SSH_PRIVATE_KEY` | Full contents of the private `.pem` file belonging to the EC2 key pair. Keep the original private and do not commit it. |
| `EC2_KNOWN_HOSTS` | Verified SSH host-key line for the same hostname/IP stored in `EC2_HOST`. |

3. Open **Settings → Actions → General** and ensure Actions and reusable
   workflows from `Roobini-code/ci-cd-pipelines` are allowed. If the pipeline
   repository is private, configure its **Actions → General → Access** settings
   to allow `java-project` to use its workflows.
4. In the same Actions settings page, allow the workflow permission to write
   repository contents. The workflow needs this to create and push release
   tags. The application workflow requests `contents: write` for that reason.
5. Protect the `main` branch under **Settings → Branches** (or repository
   rulesets) and require pull requests if only reviewed code should deploy.

#### Create `EC2_KNOWN_HOSTS` safely

Do not blindly trust a host key returned by `ssh-keyscan`. Verify the EC2
instance's SSH host key using a trusted connection or trusted AWS/admin process,
then store a `known_hosts` entry matching `EC2_HOST`. For example, an entry has
this form:

```text
ec2-203-0-113-10.compute-1.amazonaws.com ssh-ed25519 AAAA...
```

The hostname (or IP) at the beginning must match `EC2_HOST` exactly. If the
instance is replaced and its SSH host key changes, verify the new key and
update the secret.

### 2. Check the reusable pipeline repository

In the separate `ci-cd-pipelines` repository, confirm that
`.github/workflows/taskboard-java.yml` is present on its default branch and
that the repository's Actions settings allow this workflow to be called by
`java-project`.

The application currently references the reusable workflow using `@main`.
That is convenient while setting this up, but it means changes to the reusable
workflow take effect immediately. For a production pipeline, publish a
reviewed version tag or pin the caller's `uses:` line to an approved commit
SHA.

### 3. Prepare Docker Hub

In Docker Hub:

1. Confirm the `roobinidevops/taskboard-java` repository exists.
2. Confirm the configured account is allowed to push to it.
3. Create a token with **Read & Write** access.
4. Store the username and token as `DOCKERHUB_USERNAME` and `DOCKERHUB_TOKEN`
   in the **java-project GitHub repository**, not in the pipeline repository,
   workflow file, or source code.

If the repository is private, this pipeline currently configures Docker Hub
login for image publishing. The EC2 host must also be able to pull the private
image; configure Docker Hub login on EC2 or update the deployment workflow to
authenticate on EC2 before deploying.

### 4. Prepare the EC2 instance and network

The EC2 instance must:

- Be reachable at the host set in `EC2_HOST`.
- Accept SSH as `ec2-user` using the private key in `EC2_SSH_PRIVATE_KEY`.
- Have Docker Engine and `curl` installed.
- Allow `ec2-user` to run Docker commands with `sudo`.
- Have inbound SSH access from the GitHub Actions runner.
- Allow inbound HTTP on port 80 from the users who need to access Taskboard.

GitHub-hosted runner public IP addresses change. Do not assume a narrow,
permanent EC2 SSH allowlist can be maintained for the default GitHub-hosted
runner. For a tighter network policy, use a properly secured self-hosted runner
with restricted egress, or replace SSH deployment with AWS Systems Manager
using short-lived AWS credentials and least-privilege IAM permissions. Keep
port 80 restricted to the intended users; the Taskboard app has no
authentication.

The deployment creates or reuses a Docker volume named `taskboard-data`. Do
not remove this volume if task data must be preserved.

### 5. Enable safe merges and deployment

1. Push these workflow and documentation files to the GitHub repositories:
   - App workflow and this guide in `java-project`.
   - Reusable workflow in `ci-cd-pipelines`.
2. Add the five Actions secrets to `java-project`.
3. Confirm the cross-repository reusable workflow access is enabled.
4. Create a pull request targeting `main`.
5. In the PR's **Checks** section, confirm the Taskboard workflow passes both
   Maven verification and Docker image build.
6. Merge the PR. The push to `main` should trigger image publish, EC2 deploy,
   HTTP health check, and then Git tag creation.
7. Confirm the run succeeds under **Actions** in `java-project`; verify the
   versioned image in Docker Hub, the running container on EC2, and the new Git
   tag under **Releases/Tags** in GitHub.

## Troubleshooting

| Symptom | Check |
| --- | --- |
| Reusable workflow cannot be found or called | Confirm the workflow file exists on the referenced branch, the repository owner/name and path in `uses:` are exact, and cross-repository Actions access permits the caller. |
| Docker login or push fails with insufficient scopes | Create a new Docker Hub token with **Read & Write** permission and update `DOCKERHUB_TOKEN` in `java-project`. |
| `Required Actions secret ... is not configured` | Check that all five secrets are set in the app repository with the exact names above. Secrets are unavailable to fork PRs by design. |
| SSH timeout | Check `EC2_HOST`, instance status, security group SSH ingress, and whether the chosen runner can reach the instance. |
| SSH host key verification fails | Verify the instance host key and update `EC2_KNOWN_HOSTS`; do not disable strict host-key checking. |
| `Permission denied (publickey)` | Check that the private key matches the instance key pair, is stored in full, and the SSH user is `ec2-user`. |
| Docker command fails on EC2 | Confirm Docker is installed and `ec2-user` can run `sudo docker`. |
| Health check fails | Inspect `sudo docker ps` and `sudo docker logs --tail=100 taskboard` on EC2. The workflow attempts to restore the previous image if the new container does not become healthy. |
| Tag push is rejected | Confirm workflow `contents: write` permission is enabled and repository rules do not block the GitHub Actions bot from creating tags. |

For EC2 provisioning and manual deployment instructions, see
[`EC2-DEPLOYMENT-GUIDE.md`](EC2-DEPLOYMENT-GUIDE.md).
