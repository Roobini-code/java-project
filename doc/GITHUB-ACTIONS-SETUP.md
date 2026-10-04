# GitHub Actions CI/CD setup for Taskboard

This checklist explains what the Taskboard GitHub Actions pipeline does, what
you need to configure, and which repository or service to configure it in.

## Do I need a GitHub PAT for Actions to clone this repository?

No. The workflow uses `actions/checkout`, which checks out the application
repository using the short-lived `GITHUB_TOKEN` that GitHub creates for each
workflow run. You do not need to create or store a personal access token (PAT)
for Actions to clone this public app repository.

The app workflow calls a reusable workflow from
`Roobini-code/ci-cd-pipelines`. That repository must be public, or its Actions
settings must explicitly allow `java-project` to use the workflow. This
repository-to-repository workflow access is configured in GitHub; it does not
require putting your personal PAT in Actions secrets.

The PAT you created to push `.github/workflows/taskboard.yml` from your
computer is only for your local Git authentication. It is separate from
Actions runtime authentication. The pipeline also does not need AWS access
keys: its current EC2 deployment connects over SSH using the private key stored
as a repository secret.

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

If you do not have an EC2 instance yet, create one in the AWS Console:

1. Sign in to an AWS account you are authorized to use. Protect the account
   with MFA, avoid using the root account for routine administration, and
   select the AWS Region where you want the instance. EC2, EBS storage, public
   IPv4 addresses, and data transfer may incur charges. Check current pricing
   and billing alerts before launching.
2. Open **EC2 → Instances → Launch instances** and name the instance
   `taskboard-ec2`.
3. Under **Application and OS Images**, select **Amazon Linux 2023 AMI**,
   64-bit x86.
4. Select an instance type such as `t3.small`. Confirm its price and
   availability in your Region before proceeding.
5. Under **Key pair (login)**, create a new RSA key pair, choose the `.pem`
   private-key format, and download it. Store this file securely on your PC.
   AWS will not let you download the private key again. Do not put the key in
   Git, Docker Hub, or the pipeline repository.
6. Under **Network settings**, create a security group. Add:
   - **HTTP**, TCP port `80`, source restricted to your current public IP
     (`<your-ip>/32`) for initial private testing. Use a broader HTTP source
     only if the app is intentionally public; the app has no sign-in.
   - **SSH**, TCP port `22`, source restricted to your current public IP
     (`<your-ip>/32`) for manual administration. Do not use
     `0.0.0.0/0` for SSH.
   - Do not add a port `8080` rule; the container listens on `8080` internally
     and the deployment maps it to EC2 port `80`.
7. Ensure **Auto-assign public IP** is enabled. Keep the default outbound
   security-group rule that permits the instance to reach Docker Hub over
   HTTPS; the instance needs outbound network access to pull the image.
8. Leave the instance IAM role unset for this current SSH-based workflow. It
   pulls the public Docker Hub image and does not call AWS APIs from the
   instance.
9. Choose an EBS volume size (for example, `20 GiB` gp3), review the full
   estimate, and launch the instance.
10. Wait until **Instance state** is `Running` and both instance status checks
    pass. Copy its **Public IPv4 DNS** or **Public IPv4 address**; this is the
    value for `EC2_HOST`.

Connect from your Windows PC using the key pair to install Docker. In
PowerShell, substitute your downloaded key path and the instance public IP:

```powershell
ssh -i "$HOME\Downloads\taskboard-key.pem" "ec2-user@<EC2-PUBLIC-IP>"
```

On the first connection, verify that this is the instance you just launched
and accept its host key. Then, in the EC2 SSH terminal, install and start
Docker:

```bash
sudo dnf update -y
sudo dnf install -y docker curl
sudo systemctl enable --now docker
sudo docker --version
```

Exit SSH after Docker is installed. Do not add `ec2-user` to the Docker group;
the deployment uses `sudo docker`.

#### Important: GitHub Actions must be able to reach SSH

The current reusable workflow runs on GitHub-hosted `ubuntu-latest` runners and
connects to `EC2_HOST` on SSH port `22`. The SSH rule limited to your home IP
allows your PC to connect, but **does not allow GitHub's runner to deploy**.
Standard GitHub-hosted runners do not have a single fixed outbound IP suitable
for a permanent EC2 security-group allowlist.

Before relying on automatic deployment, choose one of these approaches:

1. Use a GitHub Actions runner option that provides a static outbound IP, then
   configure the reusable workflow to use that runner and allow only its IP
   (`/32`) on the EC2 security group's SSH rule. Changing the security group
   alone is not enough; the current workflow uses `ubuntu-latest`. Check plan
   requirements and pricing with GitHub.
2. Change the reusable workflow to deploy through AWS Systems Manager (SSM)
   with GitHub OIDC and least-privilege IAM permissions. This avoids opening
   inbound SSH to the runner, but **is not the deployment method implemented
   in the current workflow**; the workflow and setup guide must be updated
   before using SSM.

Do not solve this by allowing SSH from `0.0.0.0/0` or by copying broad,
changing GitHub runner IP ranges into the security group. Until a safe runner
network path is configured, PR verification and Docker build can run, but the
post-merge EC2 deployment will fail to connect.

For the current SSH workflow, the EC2 instance must:

- Be reachable at the host set in `EC2_HOST`.
- Accept SSH as `ec2-user` using the private key in `EC2_SSH_PRIVATE_KEY`.
- Have Docker Engine and `curl` installed.
- Allow `ec2-user` to run Docker commands with `sudo`.
- Allow inbound SSH only from the selected runner's static egress IP, once
  that runner option is configured.
- Allow inbound HTTP on port 80 from the people who need to access Taskboard.

#### Make Docker Hub pullable by EC2

The workflow logs in to Docker Hub on the GitHub runner to publish the image,
but does not log in to Docker Hub on EC2. Set
`roobinidevops/taskboard-java` to **Public** in Docker Hub so EC2 can pull the
image without credentials. Do not make it private unless the EC2 deployment
step is also changed to authenticate to Docker Hub.

The deployment creates or reuses a Docker volume named `taskboard-data`. Do
not remove this volume if task data must be preserved.

#### Add the EC2 values as GitHub Actions secrets

After the instance and Docker are ready, in the `java-project` GitHub
repository, open **Settings → Secrets and variables → Actions** and create
these **repository secrets**:

| Secret | Value |
| --- | --- |
| `DOCKERHUB_USERNAME` | `roobinidevops` or the Docker Hub account with push access to the image repository. |
| `DOCKERHUB_TOKEN` | Docker Hub access token with **Read & Write** permission. This is not a GitHub PAT. |
| `EC2_HOST` | EC2 public DNS name or IPv4 address, without `http://`, `https://`, or a port. |
| `EC2_SSH_PRIVATE_KEY` | Entire contents of the downloaded `.pem` private key. Keep this value secret. |
| `EC2_KNOWN_HOSTS` | The EC2 SSH host-key entry verified for the exact hostname/IP in `EC2_HOST`. |

For `EC2_KNOWN_HOSTS`, preserve strict host-key checking: verify the instance's
SSH host key via a trusted connection or AWS/admin process, and store the
matching `known_hosts` line. Do not disable host-key checking or trust an
unverified key scan. If the EC2 instance is replaced and its host key changes,
verify the new key and update both EC2-related secrets as needed.

These are the only runtime secrets the current workflow expects. Do not add
your GitHub PAT, AWS console password, AWS access key, or EC2 key to the
pipeline repository or commit them to either Git repository. Actions secrets
belong in **Settings → Secrets and variables → Actions** in `java-project`.

### 5. Enable safe merges and deployment

1. Push these workflow and documentation files to the GitHub repositories:
   - App workflow and this guide in `java-project`.
   - Reusable workflow in `ci-cd-pipelines`.
2. Add the five Actions secrets to `java-project`.
3. Confirm the cross-repository reusable workflow access is enabled.
4. Create a pull request targeting `main`.
5. In the PR's **Checks** section, confirm the Taskboard workflow passes both
   Maven verification and Docker image build.
6. Before merging, make sure the runner-to-EC2 SSH path described above is
   configured. If it is not configured, PR verification can still pass but
   deployment after merge will fail.
7. Merge the PR. The push to `main` should trigger image publish, EC2 deploy,
   HTTP health check, and then Git tag creation.
8. Confirm the run succeeds under **Actions** in `java-project`; verify the
   versioned image in Docker Hub, the running container on EC2, and the new Git
   tag under **Releases/Tags** in GitHub.

## Troubleshooting

### Push rejected for a workflow file

If `git push` reports:

```text
refusing to allow a Personal Access Token to create or update workflow
`.github/workflows/taskboard.yml` without `workflow` scope
```

GitHub rejected the credential Git used because it does not have permission to
create or update Actions workflow files. The commit remains in your local
branch; grant the token the needed permission, replace the saved GitHub
credential, and retry the push.

- For a **classic personal access token**, enable the `workflow` scope. Also
  retain the `repo` scope if you use the token to push to a private repository.
- For a **fine-grained personal access token**, grant access to the
  `java-project` repository and give it **Contents: Read and write** and
  **Workflows: Read and write** repository permissions.

If you authenticate with GitHub CLI, refresh its token with workflow scope:

```powershell
gh auth refresh --hostname github.com --scopes workflow
```

If Git uses a manually created PAT, replace the cached GitHub credential in
**Windows Credential Manager → Windows Credentials**: remove the saved
`git:https://github.com` credential, then push again and authenticate using
the newly authorized token when prompted. Keep the token private; never put it
in the command line, repository, or chat.

| Symptom | Check |
| --- | --- |
| Reusable workflow cannot be found or called | Confirm the workflow file exists on the referenced branch, the repository owner/name and path in `uses:` are exact, and cross-repository Actions access permits the caller. |
| Push rejected because a PAT lacks `workflow` scope | Authorize workflow updates on the token, replace the cached GitHub credential, then retry `git push origin feature/run-1`. |
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
