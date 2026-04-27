# Route53 and ACM Certificate Configuration

# Get the hosted zone for the domain
data "aws_route53_zone" "main" {
  name         = var.domain_name
  private_zone = false
}

# Create ACM certificate for the subdomain
resource "aws_acm_certificate" "app" {
    # # devopsdojo.livingdevops.org
  domain_name       = "${var.app_subdomain}.${var.domain_name}"
  validation_method = "DNS"

  tags = {
    Name        = "${var.app_subdomain}.${var.domain_name}"
    Environment = "production"
  }

  lifecycle {
    create_before_destroy = true
  }
}

# Create Route53 record for ACM certificate validation
resource "aws_route53_record" "cert_validation" {
  for_each = {
    for dvo in aws_acm_certificate.app.domain_validation_options : dvo.domain_name => {
      name   = dvo.resource_record_name
      record = dvo.resource_record_value
      type   = dvo.resource_record_type
    }
  }

  allow_overwrite = true
  name            = each.value.name
  records         = [each.value.record]
  ttl             = 60
  type            = each.value.type
  zone_id         = data.aws_route53_zone.main.zone_id
}

# Validate the ACM certificate
resource "aws_acm_certificate_validation" "app" {
  certificate_arn         = aws_acm_certificate.app.arn
  validation_record_fqdns = [for record in aws_route53_record.cert_validation : record.fqdn]
}

# Create Route53 alias record to point subdomain to ALB
resource "aws_route53_record" "app" {
  zone_id = data.aws_route53_zone.main.zone_id
  name    = "${var.app_subdomain}.${var.domain_name}"
  type    = "A"

  alias {
    name                   = kubernetes_ingress_v1.app_ingress_tls.status[0].load_balancer[0].ingress[0].hostname
    zone_id                = var.aws_alb_zoneid
    evaluate_target_health = true
  }

  depends_on = [kubernetes_ingress_v1.app_ingress_tls]
}

# Data source to get the ALB created by the ingress controller
# data "aws_lb" "alb" {
#   tags = {
#     "elbv2.k8s.aws/cluster"                   = var.cluster_name
#     "ingress.k8s.aws/stack"                   = "${var.app_namepace}/app-ingress-tls"
#   }

#   depends_on = [kubernetes_ingress_v1.app_ingress_tls]
# }

# Outputs
output "certificate_arn" {
  description = "ARN of the ACM certificate"
  value       = aws_acm_certificate.app.arn
}

output "app_url" {
  description = "URL to access the application"
  value       = "https://${var.app_subdomain}.${var.domain_name}"
}

output "route53_nameservers" {
  description = "Nameservers for the Route53 hosted zone"
  value       = data.aws_route53_zone.main.name_servers
}
