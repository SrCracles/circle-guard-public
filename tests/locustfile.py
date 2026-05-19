from locust import HttpUser, task, between
import uuid

class CircleGuardUser(HttpUser):
    """
    Locust load tests for CircleGuard microservices.
    Run with: locust -f tests/locustfile.py --host http://localhost:8180
    """
    wait_time = between(1, 3)

    def on_start(self):
        """Login and obtain JWT token before starting tasks."""
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
        """Simulate user login (high frequency)."""
        self.client.post(
            "/api/v1/auth/login",
            json={"username": "staff_guard", "password": "password"},
            headers={"Content-Type": "application/json"}
        )

    @task(2)
    def generate_qr(self):
        """Simulate QR generation for campus entry."""
        if self.jwt_token:
            self.client.get(
                "/api/v1/auth/qr/generate",
                headers={"Authorization": f"Bearer {self.jwt_token}"}
            )

    @task(1)
    def submit_survey(self):
        """Simulate health survey submission (lower frequency)."""
        self.client.post(
            "http://localhost:8086/api/v1/surveys",
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
        """Simulate QR validation at campus gate."""
        self.client.post(
            "http://localhost:8087/api/v1/gate/validate",
            json={"token": "dummy-token-for-load-test"},
            headers={"Content-Type": "application/json"}
        )

    @task(1)
    def invalid_login(self):
        """Simulate failed login attempts."""
        self.client.post(
            "/api/v1/auth/login",
            json={"username": "invalid_user", "password": "wrongpass"},
            headers={"Content-Type": "application/json"}
        )
