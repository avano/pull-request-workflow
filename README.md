# Pull Request Workflow

This project aims to simplify the pull request overview on GitHub utilizing labels and assignee field, so that the pull request state is visible
directly from the pull requests page.

It is a Java application that listens for [GitHub webhooks](https://developer.github.com/webhooks/) configured in your repository for various events
connected with the pull requests.

## Workflow

When a `review` is `requested` from a user, the corresponding label is applied and the reviewer is `set as an assignee` of the PR - the PR await his
action - to provide a review.

If the reviewer `requests changes` in the PR, the corresponding label is applied and PR is `assigned back to the author` of the PR to do the changes
needed.

On the `PR update`, previous reviews are `dismissed` and `rerequested` from all reviewers and also the PR is `assigned to them`.

Based on the configuration, if the `PR is approved` and it `fulfills all other conditions` (not draft, not wip, passing required checks, etc.)
the `PR is automatically merged`.

![example](./example.png)

## Application configuration

The application itself is configured via the [application.properties](./src/main/resources/application.properties) file in this repository.

The two main properties you can set are:

- `prw.repository-config-dir` to configure where the repository configuration files are stored
- `prw.repository-config-file-extension` file extension for repository configuration files

For more info about overriding the configuration at runtime, refer to
the [quarkus guide on overriding the configuration](https://quarkus.io/guides/config#overriding-properties-at-runtime)

## Repository configuration

In the repository, there is an example file [example.repoconfig](example.repoconfig) that contains all available configuration options for a single
repository together with their default values. You need to create a copy of this file and place it in the configuration directory, that you specified
in the application configuration. For each repository, you need to have a separate repository configuration file.

### GitHub configuration

The app reacts to the selected events emitted from GitHub using webhooks.

There are two ways of configuration, either through the webhooks directly in the repository and using a GitHub account with appropriate permissions
for the repository, or using a [GitHub app](https://docs.github.com/en/developers/apps/getting-started-with-apps/about-apps) (or so called `bot`
account) that acts on your behalf in the repository. The app method is the preferred one, as the `bot` user can also create checks in the pull
request.

#### How to create and configure a GitHub app

Navigate to `Settings > Developer settings > GitHub Apps` and hit `New GitHub App`, or use the [direct link](https://github.com/settings/apps/new)
and fill in basic information about the app.

Then disable `Expire user authorization tokens` and continue to the `Webhook` configuration.

- `Webhook URL` - url to the webhook endpoint of a running instance of this app
- `Webhook secret` - use any secret

In `Permissions & events`:

- `Repository permissions`
    - `Administration` - read only
    - `Checks` - read & write
    - `Contents` - read & write
    - `Issues` - read & write
    - `Metadata` - read
    - `Pull requests` - read & write
    - `Commit statuses` - read & write
- `Subscribe to events`
    - `Check run`
    - `Pull request`
    - `Pull request review`
    - `Status`

On the other hand, if you want to use account + token combination, you need to set up your repository to send the events: go to `Settings`
-> `Webhooks` and add a new webhook:

- `Payload URL` - url to the webhook endpoint of a running instance of this app
- `Content Type` - use `application/json`
- `Secret` - use any secret

Since the app works only with a subset of the events, select only following events to subscribe to:

- `Check runs`
- `Pull requests`
- `Pull request reviews`
- `Statuses`

and hit `Create GitHub App`. Now the app is created, save the `App ID` and create a `private key` for the app. Scroll a bit down and click
the `Generate a private key` button. This will download the private key to your computer.

Once you have the secret key, you need to `install` the app to your account/your organization, so navigate to `Install App` and install the app and
obtain the `installation id` - after installing the application, you will be redirected to the installation settings page and you can get the
installation id from the URL, that looks something like https://github.com/settings/installations/XXXXXX.

For each repository config file, you will need to provide the `Webhook URL`, `Webhook Secret`, `App ID`, `Installation ID` and `Private key` to make
the app handle the webhook events from your repository.

#### How to use the token instead of GitHub App

If you prefer to use a "real" GitHub account to perform the tasks, you need to have the `login` and `token` for the account. To create a new token,
navigate to `Settings > Developer Settings > Personal access tokens` and click on `Generate new token`, or use
the [direct link](https://github.com/settings/tokens/new). Fill in the note and optionally disable the token expiration and use the `repo`
permissions.

Then you need to configure the webhook in every repository where you want to use it. In the repository settings go to `Webhooks` and add a new
webhook:

- `Payload URL` - url to the webhook endpoint of a running instance of this app
- `Content type` - application/json
- `Secret` - use any secret

You can either select individual events to subscribe to, or you can use `everything` options.

Individual events include:

- `Check Runs`
- `Pull requests`
- `Pull request reviews`
- `Statuses`

Don't forget to make the webhook `Active`.

For each repository config file, you will need to provide the `Webhook URL`, `Webhook Secret`, `User` and `Token` to make the app handle the webhook
events from your repository.

## Running the application

You can either run the application in docker container (preferred way) or as a standard java application.

### Docker

In this repository there is a [Dockerfile](./Dockerfile) used to build the docker image with the application. To build the docker image, use following
commands:

```bash
./mvnw clean package
docker build . -t <image tag>
docker run -p <your preferred port>:8080 <image tag>
```

You can then access the application on `http://localhost:<your port>/webhook`

### Java app

You can also run the application as the standard Java application. Keep in mind that the generated app isn't a fat jar (it doesn't work as a
standalone jar) and it always needs to have the folders present.

To run the app, you can use

```bash
./mvnw clean package
java -jar target/quarkus-app/quarkus-run.jar
```

## Deploying the app

### Heroku

Easiest thing to do is to deploy the app as the docker web app. You can follow the
instruction [here](https://devcenter.heroku.com/articles/container-registry-and-runtime), just to outline the basic steps, having logged in to the
Heroku cli, use these commands:

```bash
heroku container:push -v -a <app name> web
heroku container:release -v -a <app name> web
```

You can then point your GitHub webhook to https://`heroku_app_url`/webhook.

### Kubernetes

Kubernetes related resources can be found in [deploy](deploy/k8s) directory. Change the config values in the [config map](deploy/k8s/01_configmap.yml)
and if you use a custom built image, change that in the [deployment](deploy/k8s/05_deployment.yml) and then create the resources using:

```bash
kubectl create -f deploy/k8s
```

One of the resources is the k8s [ingress](https://kubernetes.io/docs/concepts/services-networking/ingress/) and you can point the webhook to http:
//`ingress_ip`/webhook.

This project uses Quarkus, the Supersonic Subatomic Java Framework.

If you want to learn more about Quarkus, please visit its website: https://quarkus.io/ .
