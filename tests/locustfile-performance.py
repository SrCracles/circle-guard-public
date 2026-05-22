from locust import HttpUser, task, between
import uuid
import os

FORM_SERVICE_URL = os.environ.get("FORM_SERVICE_URL", "http://circleguard-form-service:8086")
GATEWAY_SERVICE_URL = os.environ.get("GATEWAY_SERVICE_URL", "http://circleguard-gateway-service:8087")


class PerformanceUser(HttpUser):
    """
    Performance tests for CircleGuard microservices.

    Evaluates system behavior under normal or slightly above-normal load.
    Measures: response times, throughput (RPS), resource usage, stability.

    Recommended CLI args:
      --users 20 --spawn-rate 2 --run-time 60s
    """
    wait_time = between(1, 3)

    def on_start(self):
        self.client.headers = {"Content-Type": "application/json"}
        response = self.client.post(
            "/api/v1/auth/login",
            json={"username": "staff_guard", "password": "password"}
        )
        if response.status_code == 200:
            data = response.json()
            self.jwt_token = data.get("token", "")
            self.anonymous_id = data.get("anonymousId", str(uuid.uuid4()))
            self.client.headers["Authorization"] = f"Bearer {self.jwt_token}"
        else:
            self.jwt_token = ""
            self.anonymous_id = str(uuid.uuid4())

    @task(3)
    def login(self):
        self.client.post(
            "/api/v1/auth/login",
            json={"username": "staff_guard", "password": "password"},
            headers={"Content-Type": "application/json"}
        )

    @task(2)
    def generate_qr(self):
        if self.jwt_token:
            self.client.get(
                "/api/v1/auth/qr/generate",
                headers={"Authorization": f"Bearer {self.jwt_token}"}
            )

    @task(1)
    def submit_survey(self):
        self.client.post(
            f"{FORM_SERVICE_URL}/api/v1/surveys",
            json={
                "anonymousId": self.anonymous_id,
                "hasFever": False,
                "hasCough": False,
                "otherSymptoms": "",
                "responses": {"q1": "NO", "q2": "NO"}
            },
            headers={"Content-Type": "application/json"}
        )

    @task(1)
    def validate_qr(self):
        self.client.post(
            f"{GATEWAY_SERVICE_URL}/api/v1/gate/validate",
            json={"token": "dummy-token-for-load-test"},
            headers={"Content-Type": "application/json"}
        )

    @task(1)
    def invalid_login(self):
        with self.client.post(
            "/api/v1/auth/login",
            json={"username": "invalid_user", "password": "wrongpass"},
            headers={"Content-Type": "application/json"},
            catch_response=True
        ) as response:
            if response.status_code == 401:
                response.success()
            else:
                response.failure(f"Unexpected status: {response.status_code}")
