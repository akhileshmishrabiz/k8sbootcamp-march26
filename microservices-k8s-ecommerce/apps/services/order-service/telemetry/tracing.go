// Package telemetry initializes OpenTelemetry tracing from environment.
//
// No-op when OTEL_EXPORTER_OTLP_ENDPOINT is unset, so the same binary works
// whether tracing is enabled or not.
package telemetry

import (
	"context"
	"os"
	"strings"
	"time"

	log "github.com/sirupsen/logrus"
	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracehttp"
	"go.opentelemetry.io/otel/propagation"
	"go.opentelemetry.io/otel/sdk/resource"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	semconv "go.opentelemetry.io/otel/semconv/v1.26.0"
)

// Init configures the global tracer provider. Returns a shutdown func that
// flushes pending spans; safe to call even if tracing was disabled.
func Init(ctx context.Context, serviceName string) func(context.Context) error {
	endpoint := os.Getenv("OTEL_EXPORTER_OTLP_ENDPOINT")
	if endpoint == "" {
		log.Info("OTEL_EXPORTER_OTLP_ENDPOINT not set; tracing disabled")
		return func(context.Context) error { return nil }
	}

	// OTel SDK expects host:port for HTTP exporter; strip scheme + path.
	host := strings.TrimPrefix(endpoint, "http://")
	host = strings.TrimPrefix(host, "https://")
	host = strings.SplitN(host, "/", 2)[0]

	exp, err := otlptracehttp.New(ctx,
		otlptracehttp.WithEndpoint(host),
		otlptracehttp.WithInsecure(),
	)
	if err != nil {
		log.WithError(err).Error("Failed to create OTLP trace exporter; tracing disabled")
		return func(context.Context) error { return nil }
	}

	res, _ := resource.Merge(resource.Default(), resource.NewSchemaless(
		semconv.ServiceName(serviceName),
		semconv.ServiceNamespace("ecommerce"),
		semconv.DeploymentEnvironment(getEnv("OTEL_DEPLOYMENT_ENV", "dev")),
	))

	tp := sdktrace.NewTracerProvider(
		sdktrace.WithBatcher(exp, sdktrace.WithBatchTimeout(2*time.Second)),
		sdktrace.WithResource(res),
		sdktrace.WithSampler(sdktrace.ParentBased(sdktrace.AlwaysSample())),
	)
	otel.SetTracerProvider(tp)
	otel.SetTextMapPropagator(propagation.NewCompositeTextMapPropagator(
		propagation.TraceContext{},
		propagation.Baggage{},
	))

	log.WithField("endpoint", endpoint).Info("OpenTelemetry tracing initialized")

	return func(ctx context.Context) error {
		shutdownCtx, cancel := context.WithTimeout(ctx, 5*time.Second)
		defer cancel()
		return tp.Shutdown(shutdownCtx)
	}
}

func getEnv(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
