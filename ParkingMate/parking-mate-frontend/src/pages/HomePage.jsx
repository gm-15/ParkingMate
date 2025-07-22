import { useState, useEffect } from 'react';
import axios from 'axios';
import { Link } from 'react-router-dom';

function HomePage() {
  const [spaces, setSpaces] = useState([]);

  useEffect(() => {
    axios.get('http://localhost:8080/api/spaces')
      .then(response => {
        setSpaces(response.data);
      })
      .catch(error => {
        console.error('Error fetching parking spaces:', error);
      });
  }, []);

  return (
    <div>
      <h1>주차 공간 목록</h1>
      <div className="card-container">
        {spaces.length > 0 ? (
          spaces.map(space => (
            <Link to={`/spaces/${space.id}`} key={space.id} className="card-link">
              <div className="card">
                <h2>{space.address}</h2>
                <p>시간당: {space.pricePerHour}원</p>
                <p>{space.description}</p>
                <p>소유자: {space.ownerName}</p>
              </div>
            </Link>
          ))
        ) : (
          <p>등록된 주차 공간이 없습니다.</p>
        )}
      </div>
    </div>
  );
}

export default HomePage;