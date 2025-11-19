#!/bin/bash

echo "============================================"
echo "Carbon Footprint Logger - Startup Script"
echo "============================================"
echo ""

echo "Checking Maven installation..."
if ! command -v mvn &> /dev/null; then
    echo "ERROR: Maven is not installed or not in PATH"
    echo "Please install Maven and try again"
    exit 1
fi

mvn --version
echo ""

echo "Building and running the application..."
echo ""

mvn clean javafx:run

if [ $? -ne 0 ]; then
    echo ""
    echo "ERROR: Failed to run the application"
    echo "Please check the error messages above"
    exit 1
fi
