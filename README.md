# Zero-touch reseller tool

This project is an example app that makes use of the Android zero-touch
enrollment reseller API. It demonstrates how to use the Google API client
libraries to integrate an Android app with the service, for:
* authorization
* retrieving information
* updating information
* error and exception handling

The app is usable and has the following functions:
* list customers
* add customer account
* list devices for a customer
* register a new device
* delete devices

The app uses a service account to authorize with the service. The service
account JSON can be entered within the app, or specified as a managed
configuration.

A Firestore database can also be specified in the managed configuration, which
will trigger audit logging of any actions taken using the app.

This project is intended as a demonstration of how an app could interface with
the zero-touch reseller API. It is not an officially supported Google product
and it is not suitable for production use in its current form.

## Usage

To use the app you'll need two pieces of configuration:
* reseller ID
* service account

These can be configured using a managed configuration through an EMM, or you can set
them within the app through the menu.

If you don't yet have a reseller ID, apply to become a reseller through
the [Android Enterprise Partner portal](https://www.androidenterprise.dev).

To set up your service account, follow the instructions
[here](https://developers.google.com/zero-touch/guides/auth). You can then copy the entire
contents of the JSON file into the app / managed configuration.