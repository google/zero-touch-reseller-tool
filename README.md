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
* register a new devices
* delete devices

The app uses a service account to authorize with the service. The service
account JSON can be entered within the app, or specified as a managed
configuration.

A Firestore database can also be specified in the managed configuration, which
will trigger audit logging of any actions taken using the app.

This project is intended as a demonstration of how an app could interface with
the zero-touch reseller API. It is not an officially supported Google product
and it is not suitable for production use in its current form.
