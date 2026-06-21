package qoe.k8s

deny[msg] {
  input.kind == "Deployment"
  not input.metadata.labels["app"]
  msg := "Deployment must include metadata.labels.app"
}

deny[msg] {
  input.kind == "Deployment"
  c := input.spec.template.spec.containers[_]
  not c.resources
  msg := sprintf("Container %s must specify resources.requests/limits", [c.name])
}

deny[msg] {
  input.kind == "Deployment"
  c := input.spec.template.spec.containers[_]
  endswith(c.image, ":latest")
  msg := sprintf("Container %s must not use :latest tag", [c.name])
}
