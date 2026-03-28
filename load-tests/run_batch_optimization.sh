#!/bin/bash
export LC_ALL="en_US.UTF-8"

echo "Running batch size optimization tests. Expect 200k msgs each."

# Ensure DB is clear
/opt/homebrew/opt/postgresql@15/bin/psql -d chatdb -c "TRUNCATE chat_messages, room_user_activity, failed_messages;"
/opt/homebrew/sbin/rabbitmqctl purge_queue room.1 >/dev/null 2>&1 || true

BATCH_SIZES=(100 500 1000 2000 5000)
FLUSH_INTERVALS=(100 500 500 500 1000)

echo "Batch,Flush(ms),WriteTime(s),TotalWritten,Throughput(msgs/sec)" > batch_test_results.csv

for i in "${!BATCH_SIZES[@]}"; do
    b=${BATCH_SIZES[$i]}
    f=${FLUSH_INTERVALS[$i]}
    
    echo "--- Test $i: batch=$b, flush=$f ms ---"
    
    # Start consumer
    nohup java -jar consumer-v3/target/consumer-v3-1.0-SNAPSHOT.jar --app.persistence.batch-size=$b --app.persistence.flush-interval-ms=$f > consumer_$i.log 2>&1 &
    CONSUMER_PID=$!
    
    echo "Started consumer with PID $CONSUMER_PID. Waiting 5s..."
    sleep 5
    
    echo "Starting client test for 200,000 messages..."
    START_TIME=$(date +%s)
    java -jar client-part2/target/client-part2-1.0-SNAPSHOT.jar --messages 200000 --senders 20 --rooms 20 --batch 50 --delay 20 > client_test_$i.log 2>&1
    
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
    
    END_TIME=$(date +%s)
    WRITE_TIME=$((END_TIME - START_TIME))
    
    if [ $WRITE_TIME -eq 0 ]; then WRITE_TIME=1; fi
    COUNT=$(/opt/homebrew/opt/postgresql@15/bin/psql -d chatdb -tAc "SELECT COUNT(*) FROM chat_messages;")
    if [ -z "$COUNT" ]; then COUNT=0; fi
    THROUGHPUT=$((COUNT / WRITE_TIME))
    
    echo "Finished test $i. DB Write Time: ${WRITE_TIME} s, Written: ${COUNT}, Throughput: ${THROUGHPUT} msgs/sec"
    echo "$b,$f,$WRITE_TIME,$COUNT,$THROUGHPUT" >> batch_test_results.csv
    
    # Wait another 2s to flush any remainder
    sleep 2
    
    # Stop consumer
    kill -9 $CONSUMER_PID
    sleep 2
    
    # Truncate tables for next test
    /opt/homebrew/opt/postgresql@15/bin/psql -d chatdb -c "TRUNCATE chat_messages, room_user_activity, failed_messages;"
    /opt/homebrew/sbin/rabbitmqctl purge_queue room.1 >/dev/null 2>&1 || true
done

echo "Batch tests complete! Results saved in batch_test_results.csv"
