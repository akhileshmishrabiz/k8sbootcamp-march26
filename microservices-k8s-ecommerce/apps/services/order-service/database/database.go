package database

import (
	"fmt"
	"order-service/config"
	"order-service/models"
	"time"

	log "github.com/sirupsen/logrus"
	"gorm.io/driver/postgres"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
)

func Connect(cfg *config.Config) *gorm.DB {
	dsn := fmt.Sprintf(
		"host=%s user=%s password=%s dbname=%s port=%s sslmode=disable TimeZone=UTC",
		cfg.DBHost,
		cfg.DBUser,
		cfg.DBPassword,
		cfg.DBName,
		cfg.DBPort,
	)

	var db *gorm.DB
	var err error

	// Retry connection up to 5 times
	for i := 0; i < 5; i++ {
		db, err = gorm.Open(postgres.Open(dsn), &gorm.Config{
			Logger: logger.Default.LogMode(logger.Info),
		})

		if err == nil {
			log.Info("Successfully connected to database")
			break
		}

		log.WithError(err).Warnf("Failed to connect to database, retrying... (attempt %d/5)", i+1)
		time.Sleep(time.Second * 5)
	}

	if err != nil {
		log.WithError(err).Fatal("Failed to connect to database after 5 attempts")
	}

	sqlDB, err := db.DB()
	if err != nil {
		log.WithError(err).Fatal("Failed to get database instance")
	}

	sqlDB.SetMaxIdleConns(10)
	sqlDB.SetMaxOpenConns(100)
	sqlDB.SetConnMaxLifetime(time.Hour)

	return db
}

func Migrate(db *gorm.DB) {
	// GORM 1.25's AutoMigrate runs `SELECT * FROM <table> LIMIT 1` during
	// existing-schema introspection and fails with "insufficient arguments"
	// when the row contains a uuid column scanned into a Go string. HasTable
	// issues only the information_schema lookup, so skipping AutoMigrate when
	// the table is already present avoids the crash on pod restarts.
	if db.Migrator().HasTable(&models.Order{}) {
		log.Info("Tables already exist, skipping AutoMigrate")
		return
	}

	log.Info("Running database migrations...")

	err := db.AutoMigrate(
		&models.Order{},
		&models.OrderItem{},
	)

	if err != nil {
		log.WithError(err).Fatal("Failed to run migrations")
	}

	log.Info("Database migrations completed successfully")
}
