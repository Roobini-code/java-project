# Taskboard GitHub Actions and EC2 setup

Follow these steps in order: understand the workflow, prepare AWS/EC2, prepare
Docker Hub, resolve runner-to-EC2 connectivity, add GitHub configuration and
secrets last, then test a pull request and deployment.

## What the workflow does

The app repository workflow is
[`../.github/workflows/taskboard.yml`](../.github/workflows/taskboard.yml).
It calls the reusable workflow in
[`Roobini-code/ci-cd-pipelines`](https://github.com/Roobini-code/ci-cd-pipelines),
at `.github/workflows/taskboard-java.yml`.

| Event | What happens |
| --- | --- |
| Pull request targeting `main` | Checks out the app, runs `mvn clean verify`, and builds the Docker image. It does not publish or deploy. |
| Push to `main` (including a merge) | Verifies and builds, publishes a versioned image and `latest` to Docker Hub, deploys the versioned image to EC2, checks the HTTP endpoint, and creates a Git tag. |

The image tag uses the Maven project version plus the GitHub run number and
attempt, for example `1.0.0-42.1`; the corresponding Git tag is
`v1.0.0-42.1`. The EC2 deployment keeps the `taskboard-data` Docker volume so
the H2 database survives container replacement.

**Every push to `main` deploys.** Protect `main` and require reviewed pull
requests before enabling this pipeline for production.

## Do I need a GitHub PAT for Actions to clone the app?

No. `actions/checkout` uses GitHub's automatically provided, short-lived
`GITHUB_TOKEN` to check out the application repository. The PAT you use from
your PC to push workflow files is only for your local Git operation; do not
save it as an Actions secret.

The reusable workflow repository must be public, or its Actions settings must
allow `java-project` to use its workflows. This does not require adding your
personal PAT to the app repository.

The current deployment does not use AWS API credentials or AWS access keys.
It connects to EC2 over SSH using the key stored in a GitHub Actions secret.

## Step 1: Create and secure your AWS account

1. Sign in to an AWS account you are authorized to use. Enable MFA and do not
   use the root account for routine administration.
2. Select the AWS Region where you want the server.
3. Before creating resources, review AWS pricing and create a billing budget
   or alert. EC2 compute, EBS disk, public IPv4 addresses, and network usage
   can incur charges. A budget alerts you; it does not automatically cap
   charges.

## Step 2: Launch the EC2 server

In the AWS Console:

1. Open **EC2 → Instances → Launch instances**.
2. Name: `taskboard-ec2`.
3. **Application and OS Images**: select **Amazon Linux 2023**, 64-bit x86.
4. **Instance type**: choose an available type such as `t3.small`. Check
   pricing and your account's service quotas.
5. **Key pair (login)**: create a key pair, select RSA and `.pem`, and
   download the file. Store it securely on your PC. AWS will not provide the
   private key again. Never commit or upload it to a source repository.
6. **Network settings**: create a new security group with these inbound rules:

   | Type | Port | Source |
   | --- | ---: | --- |
   | SSH | 22 | Your current public IPv4 address only (`your-ip/32`) for PC administration. |
   | HTTP | 80 | Your current public IPv4 address only (`your-ip/32`) for initial private testing. |

   Do not allow SSH from `0.0.0.0/0`. Do not add an inbound rule for port
   `8080`; Docker maps public port 80 to the container's port 8080. Taskboard
   does not have authentication, so do not expose HTTP publicly unless you
   intend anyone on the internet to access it.

7. Enable **Auto-assign public IP**. Keep outbound access enabled so the
   instance can download packages and pull a public image from Docker Hub.
8. For storage, choose an EBS size suitable for your use (for example, 20 GiB
   gp3). Check the monthly price before launching.
9. No EC2 IAM role is needed for the current SSH deployment. The current
   workflow does not use AWS APIs on the instance.
10. Review and launch. Wait for **Instance state: Running** and both instance
    status checks to pass. Copy the instance's **Public IPv4 DNS** or **Public
    IPv4 address** and keep it for the later `EC2_HOST` secret.

If you stop and start the instance, its public IP and DNS name may change.
Update the GitHub secret if they change. An Elastic IP can preserve the
address, but may incur charges.

## Step 3: Install and verify Docker on EC2

On your Windows PC, open PowerShell. Replace the key file path and public IP
with your actual values:

```powershell
ssh -i "$HOME\Downloads\taskboard-key.pem" "ec2-user@<EC2-PUBLIC-IP>"
```

On first connection, verify that the host is your newly launched instance
before accepting its SSH host key. In the EC2 terminal, install Docker:

```bash
sudo dnf update -y
sudo dnf install -y docker curl
sudo systemctl enable --now docker
sudo docker --version
```

The workflow uses `sudo docker`, so do not add `ec2-user` to the Docker group.
Keep the SSH terminal available until you have verified the key and host
information needed in Step 6.

## Step 4: Prepare Docker Hub

1. Sign in to Docker Hub and confirm the repository
   [`roobinidevops/taskboard-java`](https://hub.docker.com/repository/docker/roobinidevops/taskboard-java)
   exists and the account can publish to it.
2. Set the image repository to **Public**. The current workflow logs in on the
   GitHub runner to push images, but EC2 does not log in to Docker Hub to pull
   them. A private repository will therefore fail during deployment unless
   the workflow is extended to authenticate on EC2.
3. Create a Docker Hub access token with **Read & Write** permission. Save it
   in a password manager until Step 6. Do not use your Docker Hub account
   password or paste the token into a command.

## Step 5: Resolve GitHub-runner-to-EC2 connectivity before deployment

This is a required networking decision. The current reusable workflow uses a
GitHub-hosted `ubuntu-latest` runner and connects to EC2 on SSH port 22. Your
EC2 rule restricted to your home IP lets **your PC** connect; it does **not**
let the GitHub-hosted runner connect. Standard GitHub-hosted runners do not
have one fixed outbound IP that can safely be entered as a permanent `/32`
security-group rule.

Choose and implement one deployment route before expecting a post-merge
deployment to work:

1. **Runner with static outbound IP:** use a GitHub runner offering with a
   static egress IP. Update the reusable workflow's deployment job to run on
   that runner, then allow only its static IP (`/32`) for SSH in the EC2
   security group. Changing the security group without changing the workflow's
   runner does not solve this.
2. **AWS Systems Manager (recommended when available):** update the reusable
   workflow to deploy through SSM using GitHub OIDC and a least-privilege IAM
   role. Configure the EC2 instance as an SSM managed node. This avoids
   inbound SSH from GitHub, but it is **not implemented in the current
   workflow** and requires code and AWS configuration changes.

Do not open SSH to `0.0.0.0/0` or add broad, changing GitHub runner IP ranges
to the security group. Until a supported network route is configured, pull
request tests can pass but the deploy job will fail to connect. You can still
finish the remaining setup and run PR CI while deciding on the deployment
route.

## Step 6: Configure the GitHub repositories

In `java-project`:

1. Open **Settings → Actions → General**. Ensure Actions are enabled and
   workflow permissions allow the caller workflow to write repository
   contents; it needs this permission to create and push Git tags.
2. Protect the `main` branch or create a ruleset requiring pull requests.
   Every push to `main` runs the deployment.

In `ci-cd-pipelines`:

1. Confirm `.github/workflows/taskboard-java.yml` is pushed to its `main`
   branch.
2. If it is private, open **Settings → Actions → General → Access** and allow
   `java-project` to use its reusable workflows. If public, confirm the app
   repo's Actions policy permits using workflows from it.
3. The app currently references the reusable workflow at `@main`. For
   production, pin this reference to a reviewed version tag or commit SHA.

## Step 7: Create GitHub Actions secrets (do this last)

After EC2, Docker Hub, and GitHub repository access/permissions are ready, add
the secrets in the **`Roobini-code/java-project` app repository**:

1. Open **Settings → Secrets and variables → Actions**.
2. Select **New repository secret**.
3. Add these five secrets exactly as named:

   | Secret name | Value |
   | --- | --- |
   | `DOCKERHUB_USERNAME` | Docker Hub username with permission to push the image; currently `roobinidevops`. |
   | `DOCKERHUB_TOKEN` | The Docker Hub **Read & Write** token created in Step 4. This is not a GitHub PAT. |
   | `EC2_HOST` | The EC2 **Public IPv4 DNS** or **Public IPv4 address** from Step 2. Do not include a scheme or port. |
   | `EC2_SSH_PRIVATE_KEY` | The complete contents of the downloaded `.pem` file from Step 2. Keep it secret. |
   | `EC2_KNOWN_HOSTS` | A verified SSH host-key line for the exact host saved in `EC2_HOST`. |

4. For `EC2_KNOWN_HOSTS`, use the host key verified during your trusted first
   SSH connection in Step 3. Compare the SSH fingerprint with one obtained
   through a trusted AWS/admin channel; do not treat blindly accepting the
   first SSH prompt as verification. On Windows, after the verified connection,
   inspect the known-hosts entry with:

   ```powershell
   ssh-keygen -F "<EC2-PUBLIC-IP>" -f "$HOME\.ssh\known_hosts"
   ```

   If `EC2_HOST` is the public DNS name, search using that name instead of the
   IP. Copy the complete matching line (hostname, key type, and key) into the
   secret. The hostname at the beginning must exactly match `EC2_HOST`. If
   there is no matching entry or you cannot verify its fingerprint, do not
   disable strict host-key checking; verify the key through a trusted
   AWS/admin process first.
5. For `EC2_SSH_PRIVATE_KEY`, open the downloaded `.pem` file locally and copy
   its entire contents, including the `BEGIN` and `END` lines and all
   intervening lines. Paste those contents into the secret value without
   trimming or reformatting the newlines. Do not paste the private key into
   chat, a terminal command, or a repository.
6. Store secrets only in **java-project → Settings → Secrets and variables →
   Actions**. Do not put them in `ci-cd-pipelines`, workflow YAML, source code,
   or documentation. Never add the GitHub PAT, AWS console password, or AWS
   access keys as workflow secrets for this SSH-based workflow.

GitHub masks Actions secrets in logs and does not show their saved values
later. Keep the original EC2 private key and Docker token securely so you can
replace a secret if necessary.

## Step 8: Run the pipeline and verify it

1. Push the app workflow and guide to a feature branch and create a PR
   targeting `main`.
2. Open the PR's **Checks** tab. Confirm both Maven verification and the
   Docker image build pass. PRs do not publish images or deploy.
3. Do not merge until you have resolved Step 5's runner-to-EC2 connectivity
   requirement if you expect deployment to succeed.
4. Merge the PR. A push to `main` should run verification again, publish the
   versioned image and `latest`, deploy the versioned image, check the app over
   HTTP, and create a Git tag.
5. In the app repository's **Actions** tab, inspect the run. Confirm the
   versioned image appears in Docker Hub, `taskboard` is running on EC2, the
   EC2 HTTP endpoint returns a successful response, and the matching Git tag
   exists.

## Troubleshooting

| Problem | What to check |
| --- | --- |
| Actions cannot find/call the reusable workflow | Confirm the workflow path and branch, repository name, and cross-repository Actions access. |
| Docker push says token has insufficient scopes | Replace `DOCKERHUB_TOKEN` with a Docker Hub token having Read & Write permission. |
| SSH timeout after merge | The GitHub runner likely cannot reach EC2. Recheck Step 5; allowing only your home IP is not enough for a GitHub-hosted runner. |
| SSH host-key verification fails | Verify the instance key and update `EC2_KNOWN_HOSTS`; do not disable host-key checking. |
| `Permission denied (publickey)` | Confirm the private key is complete and belongs to the EC2 key pair, and that the SSH user is `ec2-user`. |
| Docker pull denied on EC2 | Confirm the Docker Hub image repository is Public. The current deploy workflow does not log in to Docker Hub on EC2. |
| Container is unhealthy | On EC2, run `sudo docker ps` and `sudo docker logs --tail=100 taskboard`. The workflow attempts to restore the prior image after a failed health check. |
| Push of `.github/workflows/taskboard.yml` rejected | This is your local Git credential, not Actions. A classic PAT needs `workflow` (and `repo` for private repos); a fine-grained PAT needs `Contents: Read and write` and `Workflows: Read and write`. Replace the cached Git credential and push again. |
| Git tag push rejected | Confirm the workflow has `contents: write` and repository rules permit Actions to create tags. |

For manual AWS provisioning details, see
[`EC2-DEPLOYMENT-GUIDE.md`](../EC2-DEPLOYMENT-GUIDE.md).
