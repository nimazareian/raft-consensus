import React from 'react';
import styled from "styled-components";

export const PortfolioWrapper = styled.div`
  grid-column: span 2;
  border: 2px solid grey;
  padding: 1.5rem;
  border-radius: 15px;
`
const PortFolio = () => {
    return (
        <PortfolioWrapper>
            <h1>Portfolio:</h1>
        </PortfolioWrapper>
    );
};

export default PortFolio;
