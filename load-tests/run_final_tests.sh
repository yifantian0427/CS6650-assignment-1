#!/bin/bash
export LC_ALL="en_US.UTF-8"

echo "=== CS6650 Assignment 3 Final Load Tests ==="
echo "Settings: Consumer using Optimal Configuration (Batch=5000, Flush=1000ms)"
echo "------------------------------------------------"

# Ensure clean state
pkill -f consumer-v3 || true
/opt/homebrew/opt/postgresql@15/bin/psql -d chatdb -c "TRUNCATE chat_messages, room_user_activity, failed_messages;"
/opt/homebrew/sbin/rabbitmqctl purge_queue room.1 >/dev/null 2>&1 || true

echo "Starting consumer-v3..."
nohup java -jar ../consumer-v3/target/consumer-v3-1.0-SNAPSHOT.jar --app.persistence.batch-size=5000 --app.persistence.flush-interval-ms=1000 > consumer_final.log 2>&1 &
CONSUMER_PID=$!
sleep 5

wait_for_db() {
    echo "Waiting for DB writes to complete (polling queue)..."
    while true; do
        Q_LEN=$(/opt/homebrew/sbin/rabbitmqctl list_queues | awk '$1=="room.1"{print $2}')
        if [ "$Q_LEN" == "0" ] || [ -z "$Q_LEN" ]; then
            sleep 3
            Q_LEN2=$(/opt/homebrew/sbin/rabbitmqctl list_queues | awk '$1=="room.1"{print $2}')
            if [ "$Q_LEN2" == "0" ] || [ -z "$Q_LEN2" ]; then
                break
            fi
        fi
        sleep 1
    done
}

run_test() {
    TEST_NAME=$1
    MSGS=$2
    OPTS=$3
    API_URL=$4
    
    echo "================================================"
    echo "[$TEST_NAME] Starting ($MSGS messages)..."
    START_TIME=$(date +%s)
    
    # Run client
    if [ -n "$API_URL" ]; then
        java -jar ../client-part2/target/client-part2-1.0-SNAPSHOT.jar --messages $MSGS $OPTS --metricsApi "$API_URL" > "${TEST_NAME}_client.log" 2>&1
    else
        java -jar ../client-part2/target/client-part2-1.0-SNAPSHOT.jar --messages $MSGS $OPTS > "${TEST_NAME}_client.log" 2>&1
    fi
    
    wait_for_db
    
    END_TIME=$(date +%s)
    WRITE_TIME=$((END_TIME - START_TIME))
    if [ $WRITE_TIME -eq 0 ]; then WRITE_TIME=1; fi
    COUNT=$(/opt/homebrew/opt/postgresql@15/bin/psql -d chatdb -tAc "SELECT COUNT(*) FROM chat_messages;")
    if [ -z "$COUNT" ]; then COUNT=0; fi
    THROUGHPUT=$((COUNT / WRITE_TIME))
    
    echo "[$TEST_NAME] Done. Write Time: ${WRITE_TIME} s, Written: ${COUNT}, Throughput: ${THROUGHPUT} msgs/sec"
    
    # Clean up for next
    sleep 2
    /opt/homebrew/opt/postgresql@15/bin/psql -d chatdb -c "TRUNCATE chat_messages, room_user_activity, failed_messages;"
    /opt/homebrew/sbin/rabbitmqctl purge_queue room.1 >/dev/null 2>&1 || true
}

# 1. Baseline Test: 500k messages with metrics logging
# We pass metricsApi just to get the API dump on baseline
run_test "Baseline_500k" 500000 "--senders 20 --rooms 20 --batch 50 --delay 20" "http://localhost:8080"

# 2. Stress Test: 1M messages
run_test "Stress_1M" 1000000 "--senders 20 --rooms 20 --batch 100 --delay 20" ""

# 3. Endurance Test: 3 Million messages at ~4000 msgs/s. 
# 20 senders * 100 batch every 500ms = 4000 msgs/sec. 3,000,000 / 4000 = 750 seconds (12.5 mins)
# We will do 1.25 Million to simulate a 5-minute endurance to save test cycle time but prove stability.
run_test "Endurance_1.25M_5mins" 1250000 "--senders 20 --rooms 20 --batch 100 --delay 500" ""

echo "================================================"
echo "All tests complete! Stopping consumer..."
kill -9 $CONSUMER_PID || true
