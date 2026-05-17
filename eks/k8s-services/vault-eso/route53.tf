# Route53 wildcard record (*.livingdevops.org) is managed by the argocd
# terraform state and already points to the shared ALB (group.name
# "k8sbatch-shared-alb"), which also fronts this vault ingress.
