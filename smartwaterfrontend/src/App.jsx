import { useState, useEffect } from 'react'

function App() {
  const [message, setMessage] = useState("Connecting to backend...")

  useEffect(() => {
    // Reaching out to your Spring Boot API
    fetch("http://localhost:8080/api/test")
      .then(response => response.text())
      .then(data => setMessage(data))
      .catch(error => setMessage("Error: Could not connect to backend. Is Spring Boot running?"));
  }, [])

  return (
    <div style={{ textAlign: "center", marginTop: "100px", fontFamily: "sans-serif" }}>
      <h1>AquaTrack Architecture Test</h1>
      <h2 style={{ color: message === "Connection Successful!" ? "green" : "red" }}>
        Status: {message}
      </h2>
    </div>
  )
}

export default App